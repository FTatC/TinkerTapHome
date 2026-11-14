package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Fake för tester. [run] öppnar sessionen och väntar, som den riktiga, tills [connection] inte längre
 * är ansluten. Är [runError] satt misslyckas [run] i stället direkt med det felet, kopplar ner och
 * visar [NodeSession.Failed]. Sätt sessionen direkt med [setSession].
 */
class FakeNodeSessions(private val connection: DeviceConnection) : NodeSessions {

    private val sessions = ConcurrentHashMap<BleAddress, MutableStateFlow<NodeSession>>()

    /** Om satt misslyckas [run] med det här felet. */
    @Volatile
    var runError: NodeSessionError? = null

    /** Det [send] svarar med. Kan vänta, så att testet kan se tillståndet medan ett kommando pågår. */
    var sendResult: suspend (NodeCommand) -> Outcome<Unit, NodeFailure> = { Outcome.Success(Unit) }

    /** Varje anrop till [run], med tokenet, i ordning. */
    val runCalls: MutableList<Pair<BleAddress, AuthToken?>> = mutableListOf()

    /** Kommandon som har skickats, i ordning. */
    val sent: MutableList<Pair<BleAddress, NodeCommand>> = mutableListOf()

    /** Adresser som [reset] har anropats med, i ordning. */
    val resetCalls: MutableList<BleAddress> = mutableListOf()

    /** Utgångarna som en öppen session visar. */
    var outputs: NodeOutputs = NodeOutputs(testNodeInfo(), listOf(0))

    fun setSession(address: BleAddress, session: NodeSession) {
        sessionOf(address).value = session
    }

    override fun observe(address: BleAddress): StateFlow<NodeSession> = sessionOf(address)

    override suspend fun run(address: BleAddress, token: AuthToken?): NodeSessionError? {
        runCalls += address to token
        runError?.let { error ->
            connection.disconnect(address)
            setSession(address, NodeSession.Failed(error))
            return error
        }
        setSession(address, NodeSession.Open(outputs))
        val ended = connection.observeState(address).first { it != ConnectionState.Connected }
        setSession(address, NodeSession.Closed(outputs))
        return (ended as? ConnectionState.Failed)?.let { NodeSessionError.Node(NodeFailure.Connection(it.error)) }
    }

    override suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure> {
        sent += address to command
        return sendResult(command)
    }

    override fun reset(address: BleAddress) {
        resetCalls += address
        sessionOf(address).update { if (it is NodeSession.Failed) NodeSession.Closed(it.outputs) else it }
    }

    private fun sessionOf(address: BleAddress): MutableStateFlow<NodeSession> =
        sessions.computeIfAbsent(address) { MutableStateFlow(NodeSession.Closed()) }
}
