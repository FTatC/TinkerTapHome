package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError

/**
 * Pratar TinkerTapNodes protokoll med en ansluten nod. Implementeras i `data`-lagret ovanpå
 * [io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection], som sköter anslutningen.
 */
interface NodeClient {

    /** Läser och tolkar nodens Info. Ger ett fel om noden kör en annan protokollversion än appen stöder. */
    suspend fun readInfo(address: BleAddress): Outcome<NodeInfo, NodeFailure>

    /** Skickar [command] och väntar på nodens svar. Lyckas bara om noden svarar OK. */
    suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure>
}

/** Varför ett anrop till en nod misslyckades. */
sealed interface NodeFailure {

    /** Anslutningen misslyckades eller bröts. */
    data class Connection(val error: ConnectionError) : NodeFailure

    /** Noden beskriver sig själv på ett sätt som appen inte kan använda, till exempel ett annat protokoll. */
    data class Node(val error: NodeError) : NodeFailure

    /** Noden avvisade kommandot, till exempel för att tokenet var fel. */
    data class Command(val error: CommandError) : NodeFailure
}
