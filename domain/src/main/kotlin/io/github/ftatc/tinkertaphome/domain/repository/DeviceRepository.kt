package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.model.Device
import kotlinx.coroutines.flow.Flow

/** Åtkomst till enheter som användaren har sparat. Implementeras i `data`-lagret. */
interface DeviceRepository {

    /** Strömmar alla sparade enheter och skickar en ny lista varje gång de ändras. */
    fun observeDevices(): Flow<List<Device>>
}
