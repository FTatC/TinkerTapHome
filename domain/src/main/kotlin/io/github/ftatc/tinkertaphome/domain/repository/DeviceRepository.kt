package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
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
}
