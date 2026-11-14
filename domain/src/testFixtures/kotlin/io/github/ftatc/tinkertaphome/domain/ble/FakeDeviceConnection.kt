package io.github.ftatc.tinkertaphome.domain.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fake för tester. Sätt status per enhet med [setState]. [connect] och [pair] lyckas direkt och sätter
 * status till ansluten, om inte [connectError] är satt eller [holdConnect] gör att anropet väntar tills
 * testet anropar [finishConnect].
 *
 * Som den riktiga implementationen syns ett fel också i statusen, och ett avbrutet anrop ger
 * [ConnectionState.Disconnected].
 */
class FakeDeviceConnection : DeviceConnection {

    private val states = ConcurrentHashMap<BleAddress, MutableStateFlow<ConnectionState>>()

    /** Om satt misslyckas [connect] och [pair] direkt med det här felet, som också sätts i statusen. */
    @Volatile
    var connectError: ConnectionError? = null

    /** Om `true` väntar [connect] tills [finishConnect] anropas, så att testet kan se tillståndet under tiden. */
    @Volatile
    var holdConnect: Boolean = false

    private var pendingConnect: CompletableDeferred<Outcome<Unit, ConnectionError>>? = null

    /** Adresser som [connect], [pair] och [disconnect] har anropats med, i ordning. */
    val connectCalls: MutableList<BleAddress> = mutableListOf()
    val pairCalls: MutableList<BleAddress> = mutableListOf()
    val disconnectCalls: MutableList<BleAddress> = mutableListOf()

    fun setState(address: BleAddress, state: ConnectionState) {
        stateOf(address).value = state
    }

    /** Avslutar ett [connect] som väntar på grund av [holdConnect]. */
    fun finishConnect(result: Outcome<Unit, ConnectionError>) {
        checkNotNull(pendingConnect) { "Inget connect väntar" }.complete(result)
    }

    override fun observeState(address: BleAddress): StateFlow<ConnectionState> = stateOf(address)

    override suspend fun pair(address: BleAddress): Outcome<Unit, ConnectionError> {
        pairCalls += address
        return open(address, ConnectionState.Connecting)
    }

    override suspend fun connect(address: BleAddress): Outcome<Unit, ConnectionError> {
        connectCalls += address
        return open(address, ConnectionState.Waiting)
    }

    private suspend fun open(address: BleAddress, opening: ConnectionState): Outcome<Unit, ConnectionError> {
        if (stateOf(address).value == ConnectionState.Connected) return Outcome.Success(Unit)
        val result = connectError?.let { Outcome.Failure(it) } ?: run {
            setState(address, opening)
            if (holdConnect) awaitFinishConnect(address) else Outcome.Success(Unit)
        }
        val state = when (result) {
            is Outcome.Success -> ConnectionState.Connected
            is Outcome.Failure -> ConnectionState.Failed(result.error)
        }
        setState(address, state)
        return result
    }

    private suspend fun awaitFinishConnect(address: BleAddress): Outcome<Unit, ConnectionError> {
        val pending = CompletableDeferred<Outcome<Unit, ConnectionError>>()
        pendingConnect = pending
        try {
            return pending.await()
        } catch (e: CancellationException) {
            setState(address, ConnectionState.Disconnected)
            throw e
        }
    }

    override fun disconnect(address: BleAddress) {
        disconnectCalls += address
        setState(address, ConnectionState.Disconnected)
    }

    /** Det [read] returnerar per characteristic. Saknas värdet ges [ConnectionError.NotConnected]. */
    val readResults: MutableMap<CharacteristicId, Outcome<ByteArray, ConnectionError>> = mutableMapOf()

    /**
     * Enhetens svar på varje skrivning i [writeAndAwaitReply], eller `null` för [ConnectionError.NotConnected].
     * Ett svar som anroparens `isReply` inte godtar ger [ConnectionError.NoReply], som i den riktiga implementationen.
     */
    var replyWith: ((ByteArray) -> Outcome<ByteArray, ConnectionError>)? = null

    /** Värden som har skrivits med [writeAndAwaitReply], i ordning. */
    val writes: MutableList<Pair<CharacteristicId, List<Byte>>> = mutableListOf()

    override suspend fun read(address: BleAddress, characteristic: CharacteristicId): Outcome<ByteArray, ConnectionError> =
        readResults[characteristic] ?: Outcome.Failure(ConnectionError.NotConnected)

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
    ): Outcome<ByteArray, ConnectionError> {
        writes += characteristic to value.toList()
        val answer = replyWith?.invoke(value) ?: return Outcome.Failure(ConnectionError.NotConnected)
        val accepted = (answer as? Outcome.Success<ByteArray>)?.value?.let(isReply) != false
        return if (accepted) answer else Outcome.Failure(ConnectionError.NoReply)
    }

    /** Det [observeNotifications] skickar per characteristic. Saknas flödet skickas inget. */
    val notifications: MutableMap<CharacteristicId, Flow<Outcome<ByteArray, ConnectionError>>> = mutableMapOf()

    override fun observeNotifications(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Flow<Outcome<ByteArray, ConnectionError>> = notifications[characteristic] ?: emptyFlow()

    private fun stateOf(address: BleAddress): MutableStateFlow<ConnectionState> =
        states.computeIfAbsent(address) { MutableStateFlow(ConnectionState.Disconnected) }
}
