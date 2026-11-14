package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.mapError
import io.github.ftatc.tinkertaphome.domain.takeUntilFailure
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.node.NodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    override suspend fun readState(address: BleAddress, info: NodeInfo): Outcome<List<Int>, NodeFailure> =
        connection.read(address, NodeProtocol.STATE)
            .mapError(::connectionFailure)
            .flatMap { bytes -> decode(bytes, info) }

    /**
     * Följer State via notifieringar. Ett värde med fel längd avslutar flödet, eftersom appen då inte
     * vet vilket värde som hör till vilken utgång.
     */
    override fun observeState(address: BleAddress, info: NodeInfo): Flow<Outcome<List<Int>, NodeFailure>> =
        connection.observeNotifications(address, NodeProtocol.STATE)
            .map { outcome -> outcome.mapError(::connectionFailure).flatMap { bytes -> decode(bytes, info) } }
            .takeUntilFailure()

    private fun decode(bytes: ByteArray, info: NodeInfo): Outcome<List<Int>, NodeFailure> =
        decodeState(bytes, info).mapError { NodeFailure.Node(it) }

    private fun connectionFailure(error: ConnectionError): NodeFailure = NodeFailure.Connection(error)
}
