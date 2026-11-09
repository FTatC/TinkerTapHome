package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Strömmar sparade enheter med deras anslutningsstatus, i samma ordning som [ObserveDevicesUseCase].
 * Skickar en ny lista när en enhet läggs till eller tas bort och när någon enhets status ändras.
 * Fel från lagringen skickas vidare.
 */
class ObserveConnectedDevicesUseCase @Inject constructor(
    private val observeDevices: ObserveDevicesUseCase,
    private val connection: DeviceConnection,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Outcome<List<ConnectedDevice>, DeviceError>> =
        observeDevices().flatMapLatest { outcome ->
            when (outcome) {
                is Outcome.Success -> withConnections(outcome.value).map { Outcome.Success(it) }
                is Outcome.Failure -> flowOf(outcome)
            }
        }

    /** Strömmar en sparad enhet med dess status, eller `null` om ingen enhet har [id], till exempel om den har tagits bort. */
    fun device(id: Long): Flow<Outcome<ConnectedDevice?, DeviceError>> =
        invoke()
            .map { outcome -> outcome.map { devices -> devices.find { it.device.id == id } } }
            .distinctUntilChanged()

    private fun withConnections(devices: List<Device>): Flow<List<ConnectedDevice>> {
        if (devices.isEmpty()) return flowOf(emptyList())
        val withStates = devices.map { device ->
            connection.observeState(device.address).map { state -> ConnectedDevice(device, state) }
        }
        return combine(withStates) { it.toList() }
    }
}
