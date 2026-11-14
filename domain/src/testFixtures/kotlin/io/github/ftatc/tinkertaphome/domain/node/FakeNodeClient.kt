package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.takeUntilFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

/**
 * Fake för tester. Svarar med [info] och [state] och lyckas med alla kommandon, om inte [sendResult]
 * säger annat. Nya värden i State skickas med [emitState], och [endState] avslutar flödet som när
 * anslutningen tar slut.
 */
class FakeNodeClient : NodeClient {

    var info: Outcome<NodeInfo, NodeFailure> = Outcome.Success(testNodeInfo())

    var sendResult: (NodeCommand) -> Outcome<Unit, NodeFailure> = { Outcome.Success(Unit) }

    /** Adresser som [readInfo] har anropats med, i ordning. */
    val infoReads: MutableList<BleAddress> = mutableListOf()

    /** Kommandon som har skickats, i ordning. */
    val sent: MutableList<Pair<BleAddress, NodeCommand>> = mutableListOf()

    var state: Outcome<List<Int>, NodeFailure> = Outcome.Success(listOf(0))

    /** Värden i [observeState]. `null` avslutar flödet. Buffras, så att inget tappas före insamlingen. */
    private val stateUpdates = MutableSharedFlow<Outcome<List<Int>, NodeFailure>?>(replay = UPDATE_BUFFER)

    /** Skickar nya värden i State till den som följer State. */
    suspend fun emitState(update: Outcome<List<Int>, NodeFailure>) {
        stateUpdates.emit(update)
    }

    /** Avslutar [observeState], med [failure] om anslutningen bröts eller utan fel om noden kopplades ner. */
    suspend fun endState(failure: NodeFailure? = null) {
        stateUpdates.emit(failure?.let { Outcome.Failure(it) })
    }

    override suspend fun readInfo(address: BleAddress): Outcome<NodeInfo, NodeFailure> {
        infoReads += address
        return info
    }

    override suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure> {
        sent += address to command
        return sendResult(command)
    }

    override suspend fun readState(address: BleAddress, info: NodeInfo): Outcome<List<Int>, NodeFailure> = state

    override fun observeState(address: BleAddress, info: NodeInfo): Flow<Outcome<List<Int>, NodeFailure>> =
        stateUpdates.takeWhile { it != null }.map { checkNotNull(it) }.takeUntilFailure()

    private companion object {
        const val UPDATE_BUFFER = 16
    }
}

/** Info från en nod med protokoll 3 och en digital utgång, som exemplet i `docs/ble-protocol.md`. */
fun testNodeInfo(
    authRequired: Boolean = true,
    outputs: List<Output> = listOf(Output(OutputId(0), "led", OutputType.Digital)),
    heartbeatTimeout: Duration? = null,
): NodeInfo = NodeInfo(
    firmwareVersion = "0.4.0",
    protocolVersion = 3,
    name = "TinkerTapNode-A1B2",
    pairingRequired = true,
    authRequired = authRequired,
    authTimeout = if (authRequired) 10.seconds else Duration.ZERO,
    heartbeatTimeout = heartbeatTimeout,
    outputs = outputs,
)
