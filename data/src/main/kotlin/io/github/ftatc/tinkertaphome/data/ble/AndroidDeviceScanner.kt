package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.BlePermissionChecker
import io.github.ftatc.tinkertaphome.domain.repository.DeviceScanner
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

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
                emitAll(foundDevices())
            }
        }
    }

    /** Samlar unika enheter per adress och skickar hela listan efter varje ändring. Avslutas vid fel. */
    private fun foundDevices(): Flow<ScanOutcome> {
        val found = LinkedHashMap<BleAddress, ScannedDevice>()
        return source.scanEvents().transformWhile { event ->
            when (event) {
                is RawScanEvent.Result -> {
                    val device = event.toScannedDevice()
                    if (device != null && found.addOrUpdate(device)) {
                        emit(Outcome.Success(found.values.toList()))
                    }
                    true
                }
                RawScanEvent.Unavailable -> {
                    emit(Outcome.Failure(BleError.BluetoothOff))
                    false
                }
                RawScanEvent.PermissionDenied -> {
                    emit(Outcome.Failure(BleError.PermissionMissing))
                    false
                }
                is RawScanEvent.Failed -> {
                    emit(Outcome.Failure(BleError.ScanFailed(event.errorCode)))
                    false
                }
            }
        }
    }

    companion object {
        /** Högsta antal enheter i listan. Skyddar mot enheter som skickar paket med slumpade adresser. */
        const val MAX_FOUND_DEVICES = 100
    }
}

/**
 * Lägger till eller uppdaterar [device] och returnerar `true` om listan ändrades.
 *
 * En känd enhet uppdateras alltid. Är listan full ersätter en ny enhet den med svagast signal, men
 * bara om den nya har starkare signal. Annars ignoreras den.
 */
private fun MutableMap<BleAddress, ScannedDevice>.addOrUpdate(device: ScannedDevice): Boolean {
    val known = this[device.address]
    if (known == null && size >= AndroidDeviceScanner.MAX_FOUND_DEVICES) {
        val weakest = values.minBy { it.rssi }
        if (device.rssi <= weakest.rssi) return false
        remove(weakest.address)
    }
    this[device.address] = device.keepingNameFrom(known)
    return true
}

/** Annonserar enheten inget namn i ett paket behålls namnet från ett tidigare paket. */
private fun ScannedDevice.keepingNameFrom(previous: ScannedDevice?): ScannedDevice =
    if (name == null && previous?.name != null) copy(name = previous.name) else this
