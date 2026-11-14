package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.node.NodeOutputs
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.NodeSessions
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sessionerna med TinkerTapNodes, ovanpå [NodeClient]. Måste vara en enda instans, eftersom både
 * anslutningarna och gränssnittet läser samma sessioner. Tillståndet är privat och nås bara via
 * [NodeSessions].
 */
@Singleton
internal class GattNodeSessions @Inject constructor(
    private val connection: DeviceConnection,
    private val client: NodeClient,
) : NodeSessions {

    private val sessions = ConcurrentHashMap<BleAddress, MutableStateFlow<NodeSession>>()

    override fun observe(address: BleAddress): StateFlow<NodeSession> = sessionOf(address).asStateFlow()

    override suspend fun run(address: BleAddress, token: AuthToken?): NodeSessionError? {
        val session = sessionOf(address)
        session.update { NodeSession.Opening(it.outputs) }
        val error = try {
            coroutineScope { runConnected(address, token, session).also { coroutineContext.cancelChildren() } }
        } catch (e: CancellationException) {
            session.update { NodeSession.Closed(it.outputs) }
            throw e
        }
        return end(address, session, error)
    }

    override suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure> {
        if (sessionOf(address).value !is NodeSession.Open) {
            return Outcome.Failure(NodeFailure.Connection(ConnectionError.NotConnected))
        }
        return client.send(address, command)
    }

    override fun reset(address: BleAddress) {
        sessionOf(address).update { if (it is NodeSession.Failed) NodeSession.Closed(it.outputs) else it }
    }

    /**
     * Stegen i "Rekommenderat anslutningsflöde" i `docs/ble-protocol.md`, och sedan väntan på att
     * anslutningen tar slut. Returnerar felet som stoppade sessionen, eller `null`.
     */
    private suspend fun CoroutineScope.runConnected(
        address: BleAddress,
        token: AuthToken?,
        session: MutableStateFlow<NodeSession>,
    ): NodeSessionError? {
        val info = when (val read = client.readInfo(address)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return NodeSessionError.Node(read.error)
        }
        if (info.authRequired && token == null) return NodeSessionError.TokenMissing
        val ended = CompletableDeferred<NodeFailure?>()
        // Bara appen kan ändra State medan den är ansluten, eftersom noden tar emot en anslutning åt
        // gången. Därför spelar det ingen roll om notifieringarna slås på före eller efter läsningen.
        launch(start = CoroutineStart.UNDISPATCHED) { followState(address, info, session, ended) }
        val values = when (val read = client.readState(address, info)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return NodeSessionError.Node(read.error)
        }
        session.value = NodeSession.Opening(NodeOutputs(info, values))
        authenticate(address, info, token)?.let { return it }
        session.update { NodeSession.Open(checkNotNull(it.outputs)) }
        info.heartbeatTimeout?.let { timeout -> launch { sendHeartbeats(address, timeout) } }
        return ended.await()?.let(NodeSessionError::Node)
    }

    /** Uppdaterar värdena från State tills flödet tar slut. Avslutar [ended] med felet, eller `null`. */
    private suspend fun followState(
        address: BleAddress,
        info: NodeInfo,
        session: MutableStateFlow<NodeSession>,
        ended: CompletableDeferred<NodeFailure?>,
    ) {
        client.observeState(address, info).collect { outcome ->
            when (outcome) {
                is Outcome.Success -> session.update { it.withOutputs(NodeOutputs(info, outcome.value)) }
                is Outcome.Failure -> ended.complete(outcome.error)
            }
        }
        ended.complete(null)
    }

    /** Skickar `AUTH` om noden kräver det (`auth` = 1). Fel token gör att noden kopplar ner. */
    private suspend fun authenticate(address: BleAddress, info: NodeInfo, token: AuthToken?): NodeSessionError? {
        if (!info.authRequired) return null
        return when (val sent = client.send(address, NodeCommand.Authenticate(checkNotNull(token)))) {
            is Outcome.Success -> null
            is Outcome.Failure -> NodeSessionError.Node(sent.error)
        }
    }

    /**
     * Skickar `PING` så att noden inte går till säkert läge. Ett misslyckat `PING` stoppar inte
     * sessionen: bryts anslutningen märks det i State.
     */
    private suspend fun sendHeartbeats(address: BleAddress, timeout: Duration) {
        while (true) {
            delay(timeout / HEARTBEAT_DIVISOR)
            client.send(address, NodeCommand.Ping)
        }
    }

    /**
     * Avslutar sessionen. Ett fel i sessionen kopplar ner noden och visas tills [reset]. Har
     * anslutningen redan brutits är det i stället anslutningens fel som gäller, utom när noden har
     * avvisat något, till exempel ett token: det felet ska alltid visas.
     */
    private fun end(address: BleAddress, session: MutableStateFlow<NodeSession>, error: NodeSessionError?): NodeSessionError? {
        val state = connection.observeState(address).value
        if (error != null && (state == ConnectionState.Connected || !error.isConnectionFailure())) {
            if (state == ConnectionState.Connected) connection.disconnect(address)
            session.update { NodeSession.Failed(error, it.outputs) }
            return error
        }
        session.update { NodeSession.Closed(it.outputs) }
        return (state as? ConnectionState.Failed)?.let { NodeSessionError.Node(NodeFailure.Connection(it.error)) }
    }

    private fun sessionOf(address: BleAddress): MutableStateFlow<NodeSession> =
        sessions.computeIfAbsent(address) { MutableStateFlow(NodeSession.Closed()) }

    companion object {
        /** `PING` skickas med en tredjedel av nodens heartbeat som intervall (`docs/ble-protocol.md`). */
        const val HEARTBEAT_DIVISOR = 3
    }
}

/** Nya värden från State, i en session som är på väg upp eller öppen. */
private fun NodeSession.withOutputs(outputs: NodeOutputs): NodeSession = when (this) {
    is NodeSession.Opening -> NodeSession.Opening(outputs)
    is NodeSession.Open -> NodeSession.Open(outputs)
    is NodeSession.Closed, is NodeSession.Failed -> this
}

private fun NodeSessionError.isConnectionFailure(): Boolean =
    this is NodeSessionError.Node && failure is NodeFailure.Connection
