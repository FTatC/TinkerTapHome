package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.BlePermissionChecker
import io.github.ftatc.tinkertaphome.domain.repository.DeviceScanner
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    }.catch { cause ->
        // Behörigheten kan tas bort medan skanningen pågår. Android kastar då SecurityException.
        if (cause is SecurityException) emit(Outcome.Failure(BleError.PermissionMissing)) else throw cause
    }

    /** Samlar unika enheter per adress och skickar hela listan efter varje ändring. Avslutas vid fel. */
    private fun foundDevices(): Flow<ScanOutcome> {
        val found = LinkedHashMap<BleAddress, ScannedDevice>()
        return source.scanEvents().transformWhile { event ->
            when (event) {
                is RawScanEvent.Result -> {
                    event.toScannedDevice()?.let { device ->
                        found[device.address] = device.keepingNameFrom(found[device.address])
                        emit(Outcome.Success(found.values.toList()))
                    }
                    true
                }
                RawScanEvent.Unavailable -> {
                    emit(Outcome.Failure(BleError.BluetoothOff))
                    false
                }
                is RawScanEvent.Failed -> {
                    emit(Outcome.Failure(BleError.ScanFailed(event.errorCode)))
                    false
                }
            }
        }
    }
}

/** Annonserar enheten inget namn i ett paket behålls namnet från ett tidigare paket. */
private fun ScannedDevice.keepingNameFrom(previous: ScannedDevice?): ScannedDevice =
    if (name == null && previous?.name != null) copy(name = previous.name) else this
