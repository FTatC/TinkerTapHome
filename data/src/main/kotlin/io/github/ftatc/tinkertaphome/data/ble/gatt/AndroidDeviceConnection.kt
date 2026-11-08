package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.BlePermissionChecker
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Håller en [GattSession] per enhet. Måste vara en enda instans, eftersom varje session äger
 * enhetens GATT-kö (se CLAUDE.md). Tillståndet är privat och nås bara via [DeviceConnection].
 *
 * En session skapas första gången en enhet efterfrågas och finns sedan kvar, så att statusen,
 * till exempel ett fel, går att läsa även efter att anslutningen har stängts.
 */
@Singleton
internal class AndroidDeviceConnection @Inject constructor(
    private val connector: GattConnector,
    private val permissions: BlePermissionChecker,
) : DeviceConnection {

    private val sessions = ConcurrentHashMap<BleAddress, GattSession>()

    override fun observeState(address: BleAddress): StateFlow<ConnectionState> = session(address).state

    override suspend fun connect(address: BleAddress): Outcome<Unit, ConnectionError> = when {
        !permissions.isGranted() -> Outcome.Failure(ConnectionError.PermissionMissing)
        !connector.isBluetoothEnabled() -> Outcome.Failure(ConnectionError.BluetoothOff)
        else -> session(address).connect()
    }

    override fun disconnect(address: BleAddress) {
        sessions[address]?.disconnect()
    }

    override suspend fun read(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Outcome<ByteArray, ConnectionError> = session(address).read(characteristic)

    override suspend fun write(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
    ): Outcome<Unit, ConnectionError> = session(address).write(characteristic, value)

    override fun observeNotifications(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Flow<Outcome<ByteArray, ConnectionError>> = session(address).notifications(characteristic)

    private fun session(address: BleAddress): GattSession =
        sessions.computeIfAbsent(address) { GattSession(it.value, connector) }
}
