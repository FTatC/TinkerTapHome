package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import kotlinx.coroutines.flow.Flow

/**
 * Användarens val för utgångarna på sparade noder. Implementeras i `data`-lagret.
 *
 * En utgång identifieras med nodens adress och utgångens namn, inte med dess id, eftersom id kan
 * ändras mellan firmwareversioner medan namnet står kvar.
 */
interface OutputPreferencesRepository {

    /**
     * Strömmar namnen på de utgångar på noden med [address] som ska visas som tryckknappar, och skickar
     * en ny mängd varje gång valen ändras.
     *
     * Om lagringen inte går att läsa skickas [Outcome.Failure] i stället för att flödet kastar.
     * Efter ett fel avslutas flödet.
     */
    fun observePushButtons(address: BleAddress): Flow<Outcome<Set<String>, DeviceError>>

    /**
     * Sparar om utgången [outputName] på noden med [address] ska visas som tryckknapp. Valet tas bort
     * när noden tas bort. Ger [DeviceError.StorageUnavailable] om noden inte är sparad.
     */
    suspend fun setPushButton(address: BleAddress, outputName: String, pushButton: Boolean): Outcome<Unit, DeviceError>
}
