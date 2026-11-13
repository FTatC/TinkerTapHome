package io.github.ftatc.tinkertaphome.domain.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fake för tester. Sätt status per enhet med [setState]. [connect] lyckas direkt och sätter status
 * till ansluten, om inte [connectError] är satt eller [holdConnect] gör att anropet väntar tills
 * testet anropar [finishConnect].
 */
class FakeDeviceConnection : DeviceConnection {

    private val states = ConcurrentHashMap<BleAddress, MutableStateFlow<ConnectionState>>()

    /** Om satt misslyckas [connect] med det här felet utan att statusen ändras. */
    @Volatile
    var connectError: ConnectionError? = null

    /** Om `true` väntar [connect] tills [finishConnect] anropas, så att testet kan se tillståndet under tiden. */
    @Volatile
    var holdConnect: Boolean = false

    private var pendingConnect: CompletableDeferred<Outcome<Unit, ConnectionError>>? = null

    /** Adresser som [connect] och [disconnect] har anropats med, i ordning. */
    val connectCalls: MutableList<BleAddress> = mutableListOf()
    val disconnectCalls: MutableList<BleAddress> = mutableListOf()

    fun setState(address: BleAddress, state: ConnectionState) {
        stateOf(address).value = state
    }

    /** Avslutar ett [connect] som väntar på grund av [holdConnect]. */
    fun finishConnect(result: Outcome<Unit, ConnectionError>) {
        checkNotNull(pendingConnect) { "Inget connect väntar" }.complete(result)
    }

    override fun observeState(address: BleAddress): StateFlow<ConnectionState> = stateOf(address)

    override suspend fun connect(address: BleAddress): Outcome<Unit, ConnectionError> {
        connectCalls += address
        connectError?.let { return Outcome.Failure(it) }
        setState(address, ConnectionState.Connecting)
        val result = if (holdConnect) awaitFinishConnect() else Outcome.Success(Unit)
        val state = when (result) {
            is Outcome.Success -> ConnectionState.Connected
            is Outcome.Failure -> ConnectionState.Failed(result.error)
        }
        setState(address, state)
        return result
    }

    private suspend fun awaitFinishConnect(): Outcome<Unit, ConnectionError> {
        val pending = CompletableDeferred<Outcome<Unit, ConnectionError>>()
        pendingConnect = pending
        return pending.await()
    }

    override fun disconnect(address: BleAddress) {
        disconnectCalls += address
        setState(address, ConnectionState.Disconnected)
    }

    override suspend fun read(address: BleAddress, characteristic: CharacteristicId): Outcome<ByteArray, ConnectionError> =
        Outcome.Failure(ConnectionError.NotConnected)

    override suspend fun write(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
    ): Outcome<Unit, ConnectionError> = Outcome.Failure(ConnectionError.NotConnected)

    override suspend fun writeAndAwaitReply(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
        reply: CharacteristicId,
        isReply: (ByteArray) -> Boolean,
    ): Outcome<ByteArray, ConnectionError> = Outcome.Failure(ConnectionError.NotConnected)

    override fun observeNotifications(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Flow<Outcome<ByteArray, ConnectionError>> = emptyFlow()

    private fun stateOf(address: BleAddress): MutableStateFlow<ConnectionState> =
        states.computeIfAbsent(address) { MutableStateFlow(ConnectionState.Disconnected) }
}
