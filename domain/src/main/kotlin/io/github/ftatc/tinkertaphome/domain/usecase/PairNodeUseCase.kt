package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.mapError
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.NodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import javax.inject.Inject

/**
 * Första steget när en TinkerTapNode sparas: parkopplar med noden och läser dess Info, så att appen
 * vet om noden kräver ett token. Användaren ska ha tryckt på nodens parkopplingsknapp först (fråga 7
 * i `docs/open-questions.md`).
 *
 * Misslyckas något kopplas noden ner, så att den kan ta emot andra anslutningar.
 */
class PairNodeUseCase @Inject constructor(
    private val connection: DeviceConnection,
    private val nodeClient: NodeClient,
) {
    suspend operator fun invoke(address: BleAddress): Outcome<NodeInfo, NodeFailure> {
        val result = connection.pair(address)
            .mapError<Unit, _, NodeFailure> { NodeFailure.Connection(it) }
            .flatMap { nodeClient.readInfo(address) }
        if (result is Outcome.Failure) connection.disconnect(address)
        return result
    }

    /** Avbryter en påbörjad parkoppling, till exempel när användaren stänger dialogen innan noden har sparats. */
    fun abort(address: BleAddress) {
        connection.disconnect(address)
    }
}
