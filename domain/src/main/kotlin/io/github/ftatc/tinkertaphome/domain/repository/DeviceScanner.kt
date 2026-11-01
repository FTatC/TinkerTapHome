package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import kotlinx.coroutines.flow.Flow

/** Söker efter BLE-enheter i närheten. Implementeras i `data`-lagret. */
interface DeviceScanner {

    /**
     * Startar en skanning när flödet samlas in och stoppar den när insamlingen avbryts.
     *
     * Skickar först en tom lista, och sedan hela listan med unika enheter varje gång en enhet
     * hittas eller uppdateras. Vid fel skickas [Outcome.Failure] och flödet avslutas.
     */
    fun scan(): Flow<Outcome<List<ScannedDevice>, BleError>>
}
