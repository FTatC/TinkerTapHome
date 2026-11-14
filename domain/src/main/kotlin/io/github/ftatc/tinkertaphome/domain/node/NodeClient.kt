package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import kotlinx.coroutines.flow.Flow

/**
 * Pratar TinkerTapNodes protokoll med en ansluten nod. Implementeras i `data`-lagret ovanpå
 * [io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection], som sköter anslutningen.
 */
interface NodeClient {

    /** Läser och tolkar nodens Info. Ger ett fel om noden kör en annan protokollversion än appen stöder. */
    suspend fun readInfo(address: BleAddress): Outcome<NodeInfo, NodeFailure>

    /** Skickar [command] och väntar på nodens svar. Lyckas bara om noden svarar OK. */
    suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure>

    /** Läser utgångarnas värden från State. Ger ett fel om State inte har ett värde per utgång i [info]. */
    suspend fun readState(address: BleAddress, info: NodeInfo): Outcome<List<Int>, NodeFailure>

    /**
     * Följer State och skickar utgångarnas värden varje gång noden ändrar dem. Bryts anslutningen, eller
     * har State fel längd, skickas ett fel och flödet avslutas. Kopplas noden ner utan fel avslutas
     * flödet utan att skicka något mer.
     */
    fun observeState(address: BleAddress, info: NodeInfo): Flow<Outcome<List<Int>, NodeFailure>>
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
