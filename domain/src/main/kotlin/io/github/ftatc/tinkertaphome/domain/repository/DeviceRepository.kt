package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import kotlinx.coroutines.flow.Flow

/** Åtkomst till enheter som användaren har sparat. Implementeras i `data`-lagret. */
interface DeviceRepository {

    /**
     * Strömmar alla sparade enheter och skickar en ny lista varje gång de ändras.
     *
     * Om lagringen inte går att läsa skickas [Outcome.Failure] i stället för att flödet kastar.
     * Efter ett fel avslutas flödet.
     */
    fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>>

    /**
     * Sparar en ny enhet och returnerar den med sitt tilldelade id. [token] är tokenet för AUTH,
     * eller `null` om enheten inte kräver något.
     *
     * Ger [SaveDeviceError.AlreadySaved] om en enhet med samma adress redan finns.
     * Namnet sparas som det är; validering görs i use caset.
     */
    suspend fun save(address: BleAddress, name: String, token: AuthToken?): Outcome<Device, SaveDeviceError>

    /**
     * Läser tokenet för AUTH för enheten med [address]. Ger `null` om inget token är sparat eller om
     * enheten inte finns. Tokenet ligger inte i [Device], så att det inte följer med till listorna.
     */
    suspend fun authToken(address: BleAddress): Outcome<AuthToken?, DeviceError>

    /**
     * Byter tokenet för enheten med [address], till exempel efter `AUTH_FAILED` när användaren har
     * angett ett nytt. `null` tar bort det. Gör inget om enheten inte finns.
     */
    suspend fun updateAuthToken(address: BleAddress, token: AuthToken?): Outcome<Unit, DeviceError>
}
