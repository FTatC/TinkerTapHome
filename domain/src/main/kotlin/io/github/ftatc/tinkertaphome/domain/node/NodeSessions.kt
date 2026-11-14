package io.github.ftatc.tinkertaphome.domain.node

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import kotlinx.coroutines.flow.StateFlow

/**
 * Sessionerna med sparade TinkerTapNodes. Implementeras i `data`-lagret och delas mellan
 * [io.github.ftatc.tinkertaphome.domain.usecase.KeepSavedDevicesConnectedUseCase], som kör
 * sessionerna, och gränssnittet, som visar och styr utgångarna.
 */
interface NodeSessions {

    /** Sessionen med noden på [address]. Är [NodeSession.Closed] från början. */
    fun observe(address: BleAddress): StateFlow<NodeSession>

    /**
     * Kör sessionen med en nod som just har anslutits. Steg för steg: läser Info och kontrollerar
     * protokollet, följer State, läser State och skickar `AUTH` med [token] om noden kräver det.
     * Alla steg körs direkt, eftersom noden kopplar ner om `AUTH` inte kommer inom `ato`. Om noden
     * har en heartbeat skickas `PING` med ett intervall på en tredjedel av den.
     *
     * Returnerar när sessionen tar slut:
     * - `null` om noden kopplades ner utan fel, till exempel av användaren.
     * - Anslutningens fel om anslutningen bröts.
     * - Sessionens fel om ett steg misslyckades medan noden var ansluten. Noden kopplas då ner, och
     *   felet syns som [NodeSession.Failed] tills [reset] anropas.
     */
    suspend fun run(address: BleAddress, token: AuthToken?): NodeSessionError?

    /** Skickar [command] till noden. Misslyckas om sessionen inte är [NodeSession.Open]. */
    suspend fun send(address: BleAddress, command: NodeCommand): Outcome<Unit, NodeFailure>

    /** Nollställer ett fel i sessionen, så att den kan köras igen. Senast kända värden behålls. */
    fun reset(address: BleAddress)
}
