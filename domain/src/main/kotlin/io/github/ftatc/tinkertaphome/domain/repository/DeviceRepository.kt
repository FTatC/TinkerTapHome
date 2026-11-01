package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
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
     * Sparar en ny enhet och returnerar den med sitt tilldelade id.
     *
     * Ger [SaveDeviceError.AlreadySaved] om en enhet med samma adress redan finns.
     * Namnet sparas som det är; validering görs i use caset.
     */
    suspend fun save(address: BleAddress, name: String): Outcome<Device, SaveDeviceError>
}
