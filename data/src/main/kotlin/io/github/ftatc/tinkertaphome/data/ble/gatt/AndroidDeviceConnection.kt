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

    override fun isPaired(address: BleAddress): Boolean = connector.isBonded(address.value)

    override suspend fun pair(address: BleAddress): Outcome<Unit, ConnectionError> =
        whenAvailable(address) { it.pair() }

    override suspend fun connect(address: BleAddress): Outcome<Unit, ConnectionError> =
        whenAvailable(address) { it.connect() }

    /** Kör [open] om behörigheten finns och Bluetooth är på. Annars syns felet i enhetens status. */
    private suspend fun whenAvailable(
        address: BleAddress,
        open: suspend (GattSession) -> Outcome<Unit, ConnectionError>,
    ): Outcome<Unit, ConnectionError> {
        val session = session(address)
        return when {
            !permissions.isGranted() -> session.reportUnavailable(ConnectionError.PermissionMissing)
            !connector.isBluetoothEnabled() -> session.reportUnavailable(ConnectionError.BluetoothOff)
            else -> open(session)
        }
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

    override suspend fun writeAndAwaitReply(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
        reply: CharacteristicId,
        isReply: (ByteArray) -> Boolean,
    ): Outcome<ByteArray, ConnectionError> = session(address).writeAndAwaitReply(characteristic, value, reply, isReply)

    override fun observeNotifications(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Flow<Outcome<ByteArray, ConnectionError>> = session(address).notifications(characteristic)

    private fun session(address: BleAddress): GattSession =
        sessions.computeIfAbsent(address) { GattSession(it.value, connector) }
}
