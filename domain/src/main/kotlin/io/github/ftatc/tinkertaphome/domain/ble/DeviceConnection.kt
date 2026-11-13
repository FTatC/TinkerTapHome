package io.github.ftatc.tinkertaphome.domain.ble

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Anslutningar till BLE-enheter. Implementeras i `data`-lagret.
 *
 * Operationer mot samma enhet körs en i taget i den ordning de anropas, eftersom Android inte klarar
 * parallella GATT-anrop. Fel returneras som [Outcome.Failure] och kastas aldrig.
 */
interface DeviceConnection {

    /** Anslutningsstatus för enheten med [address]. Är [ConnectionState.Disconnected] från början. */
    fun observeState(address: BleAddress): StateFlow<ConnectionState>

    /**
     * Ansluter till en enhet som inte är parkopplad än, parkopplar och söker efter enhetens tjänster.
     * Används när en enhet sparas. Misslyckas om enheten inte svarar inom kort.
     * Är enheten redan ansluten lyckas anropet direkt.
     */
    suspend fun pair(address: BleAddress): Outcome<Unit, ConnectionError>

    /**
     * Ansluter till en sparad enhet och söker efter dess tjänster. Väntar med status
     * [ConnectionState.Waiting] tills enheten kommer inom räckhåll, hur länge det än dröjer.
     *
     * Parkopplar aldrig: är telefonen inte parkopplad med enheten misslyckas anropet direkt med
     * [ConnectionError.PairingMissing]. Är enheten redan ansluten lyckas anropet direkt. Fel som
     * hindrar anslutningen, till exempel avstängt Bluetooth, syns också i [observeState].
     */
    suspend fun connect(address: BleAddress): Outcome<Unit, ConnectionError>

    /**
     * Kopplar ner, eller slutar vänta på enheten. En pågående operation avbryts och får
     * [ConnectionError.NotConnected]. Ett fel i statusen nollställs till [ConnectionState.Disconnected].
     */
    fun disconnect(address: BleAddress)

    /** Läser värdet för [characteristic]. */
    suspend fun read(address: BleAddress, characteristic: CharacteristicId): Outcome<ByteArray, ConnectionError>

    /** Skriver [value] till [characteristic] och väntar på att enheten bekräftar. */
    suspend fun write(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
    ): Outcome<Unit, ConnectionError>

    /**
     * Skriver [value] till [characteristic] och väntar på enhetens svar: den första notifieringen från
     * [reply] som [isReply] godtar och som kommer efter att skrivningen har startat.
     *
     * Notifieringarna för [reply] måste redan vara påslagna med [observeNotifications]. Ingen annan
     * operation mot enheten körs innan svaret har kommit. Kommer inget svar strax efter att
     * skrivningen har bekräftats stängs anslutningen och [ConnectionError.NoReply] returneras.
     */
    suspend fun writeAndAwaitReply(
        address: BleAddress,
        characteristic: CharacteristicId,
        value: ByteArray,
        reply: CharacteristicId,
        isReply: (ByteArray) -> Boolean,
    ): Outcome<ByteArray, ConnectionError>

    /**
     * Slår på notifieringar för [characteristic] när flödet samlas in och skickar varje nytt värde.
     * Notifieringarna slås av när insamlingen avbryts.
     *
     * Går notifieringarna inte att slå på, eller bryts anslutningen, skickas [Outcome.Failure] och
     * flödet avslutas. Kopplar användaren ner avslutas flödet utan fel.
     *
     * Bara en insamlare per characteristic stöds: när en av dem slutar slås notifieringarna av för alla.
     */
    fun observeNotifications(
        address: BleAddress,
        characteristic: CharacteristicId,
    ): Flow<Outcome<ByteArray, ConnectionError>>
}
