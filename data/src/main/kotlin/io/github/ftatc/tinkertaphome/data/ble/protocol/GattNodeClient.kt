package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.mapError
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.node.NodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import javax.inject.Inject

/**
 * TinkerTapNodes protokoll ovanpå [DeviceConnection]. Kodningen och avkodningen ligger i rena
 * funktioner i samma paket, så den här klassen kopplar bara ihop dem med GATT-anropen.
 */
internal class GattNodeClient @Inject constructor(
    private val connection: DeviceConnection,
) : NodeClient {

    override suspend fun readInfo(address: BleAddress): Outcome<NodeInfo, NodeFailure> =
        connection.read(address, NodeProtocol.INFO)
            .mapError(::connectionFailure)
            .flatMap { bytes -> parseInfo(bytes).mapError { NodeFailure.Node(it) } }

    /**
     * Skriver kommandot till Command och väntar på det Result som svarar på just det kommandot (se
     * `NodeResult.answers`). Notifieringarna för Result slås på av [DeviceConnection.writeAndAwaitReply].
     */
    override suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure> =
        connection.writeAndAwaitReply(address, NodeProtocol.COMMAND, command.encode(), NodeProtocol.RESULT) { bytes ->
            NodeResult.decode(bytes)?.answers(command) == true
        }
            .mapError(::connectionFailure)
            .flatMap { bytes -> checkNotNull(NodeResult.decode(bytes)).toOutcome().mapError { NodeFailure.Command(it) } }

    private fun connectionFailure(error: ConnectionError): NodeFailure = NodeFailure.Connection(error)
}
