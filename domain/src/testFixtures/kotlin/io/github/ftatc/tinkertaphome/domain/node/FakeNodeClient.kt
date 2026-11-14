package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Fake för tester. Svarar med [info] och lyckas med alla kommandon, om inte [sendResult] säger annat. */
class FakeNodeClient : NodeClient {

    var info: Outcome<NodeInfo, NodeFailure> = Outcome.Success(testNodeInfo())

    var sendResult: (NodeCommand) -> Outcome<Unit, NodeFailure> = { Outcome.Success(Unit) }

    /** Adresser som [readInfo] har anropats med, i ordning. */
    val infoReads: MutableList<BleAddress> = mutableListOf()

    /** Kommandon som har skickats, i ordning. */
    val sent: MutableList<Pair<BleAddress, NodeCommand>> = mutableListOf()

    override suspend fun readInfo(address: BleAddress): Outcome<NodeInfo, NodeFailure> {
        infoReads += address
        return info
    }

    override suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure> {
        sent += address to command
        return sendResult(command)
    }
}

/** Info från en nod med protokoll 3 och en digital utgång, som exemplet i `docs/ble-protocol.md`. */
fun testNodeInfo(
    authRequired: Boolean = true,
    outputs: List<Output> = listOf(Output(OutputId(0), "led", OutputType.Digital)),
): NodeInfo = NodeInfo(
    firmwareVersion = "0.4.0",
    protocolVersion = 3,
    name = "TinkerTapNode-A1B2",
    pairingRequired = true,
    authRequired = authRequired,
    authTimeout = if (authRequired) 10.seconds else Duration.ZERO,
    heartbeatTimeout = null,
    outputs = outputs,
)
