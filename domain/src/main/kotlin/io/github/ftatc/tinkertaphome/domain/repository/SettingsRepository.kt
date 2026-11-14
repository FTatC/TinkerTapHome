package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import kotlinx.coroutines.flow.Flow

/** Användarens inställningar. Implementeras i `data`-lagret med DataStore. */
interface SettingsRepository {

    /**
     * Strömmar inställningarna och skickar nya varje gång de ändras. Går lagringen inte att läsa
     * skickas standardvärdena, eftersom appen ska fungera även utan sparade inställningar.
     */
    fun observeSettings(): Flow<AppSettings>

    /** Ändrar inställningarna med [transform]. Ändringen syns i [observeSettings] när den är sparad. */
    suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit, SettingsError>
}
