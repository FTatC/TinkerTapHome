package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.BlePermissionChecker
import io.github.ftatc.tinkertaphome.domain.ble.DeviceScanner
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.takeUntilFailure
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

private typealias ScanOutcome = Outcome<List<ScannedDevice>, BleError>

internal class AndroidDeviceScanner @Inject constructor(
    private val source: BleScanSource,
    private val permissions: BlePermissionChecker,
) : DeviceScanner {

    override fun scan(): Flow<ScanOutcome> = flow {
        when {
            !permissions.isGranted() -> emit(Outcome.Failure(BleError.PermissionMissing))
            !source.isBluetoothEnabled() -> emit(Outcome.Failure(BleError.BluetoothOff))
            else -> {
                emit(Outcome.Success(emptyList()))
                emitAll(scanResults())
            }
        }
    }

    /**
     * Hela listan med unika enheter efter varje ändring, eller ett fel som avslutar skanningen.
     * Händelser som inte ändrar listan, till exempel en ogiltig adress, ger inget nytt resultat.
     */
    private fun scanResults(): Flow<ScanOutcome> {
        val found = LinkedHashMap<BleAddress, ScannedDevice>()
        return source.scanEvents()
            .mapNotNull { event ->
                when (event) {
                    is RawScanEvent.Result -> found.withResult(event)?.let { Outcome.Success(it) }
                    is RawScanEvent.Stopped -> Outcome.Failure(event.toBleError())
                }
            }
            .takeUntilFailure()
    }

    companion object {
        /** Högsta antal enheter i listan. Skyddar mot enheter som skickar paket med slumpade adresser. */
        const val MAX_FOUND_DEVICES = 100
    }
}

/** Lägger till resultatet och returnerar den nya listan, eller `null` om listan inte ändrades. */
private fun MutableMap<BleAddress, ScannedDevice>.withResult(event: RawScanEvent.Result): List<ScannedDevice>? {
    val device = event.toScannedDevice() ?: return null
    return if (addOrUpdateWithinLimit(device)) values.toList() else null
}

/**
 * Lägger till eller uppdaterar [device] utan att listan blir längre än [AndroidDeviceScanner.MAX_FOUND_DEVICES].
 * Returnerar `true` om listan ändrades.
 *
 * En känd enhet uppdateras alltid. Är listan full ersätter en ny enhet den med svagast signal, men
 * bara om den nya har starkare signal. Annars avvisas den.
 */
private fun MutableMap<BleAddress, ScannedDevice>.addOrUpdateWithinLimit(device: ScannedDevice): Boolean {
    val known = this[device.address]
    if (known == null && size >= AndroidDeviceScanner.MAX_FOUND_DEVICES) {
        val weakest = values.minBy { it.rssi }
        if (device.rssi <= weakest.rssi) return false
        remove(weakest.address)
    }
    this[device.address] = device.keepingKnownFrom(known)
    return true
}

/**
 * Behåller det som ett tidigare paket har visat. Namnet ligger i svaret på en skanning och
 * service-UUID:t i reklampaketet, så ett enskilt paket har inte alltid båda.
 */
private fun ScannedDevice.keepingKnownFrom(previous: ScannedDevice?): ScannedDevice = copy(
    name = name ?: previous?.name,
    isTinkerTapNode = isTinkerTapNode || previous?.isTinkerTapNode == true,
)

private fun RawScanEvent.Stopped.toBleError(): BleError = when (this) {
    RawScanEvent.BluetoothOff -> BleError.BluetoothOff
    RawScanEvent.PermissionDenied -> BleError.PermissionMissing
    is RawScanEvent.Failed -> BleError.ScanFailed(errorCode)
}
