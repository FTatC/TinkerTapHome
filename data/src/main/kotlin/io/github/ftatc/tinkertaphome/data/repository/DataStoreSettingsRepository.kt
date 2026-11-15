package io.github.ftatc.tinkertaphome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * Inställningarna i DataStore. Värdena sparas som enkla typer: skanningens längd i sekunder och
 * temat som namnet på [ThemeMode]. Ett okänt värde, till exempel från en senare version av appen,
 * ger standardvärdet i stället för ett fel.
 */
internal class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    /**
     * Går filen inte att läsa skickas standardvärdena, och sedan försöker flödet igen efter
     * [READ_RETRY_DELAY]. Flödet tar alltså aldrig slut av ett läsfel, så att senare ändringar,
     * till exempel av temat, fortfarande når gränssnittet.
     */
    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data
            .retryWhen { error, _ ->
                (error is IOException).also { canRetry ->
                    if (canRetry) {
                        emit(emptyPreferences())
                        delay(READ_RETRY_DELAY)
                    }
                }
            }
            .map { it.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit, SettingsError> =
        try {
            dataStore.edit { preferences -> preferences.write(transform(preferences.toAppSettings())) }
            Outcome.Success(Unit)
        } catch (_: IOException) {
            Outcome.Failure(SettingsError.StorageUnavailable)
        }

    companion object {
        /** Paus innan inställningarna läses igen efter ett fel. */
        val READ_RETRY_DELAY: Duration = 5.seconds
    }
}

private val SCAN_DURATION_SECONDS = intPreferencesKey("scan_duration_seconds")
private val THEME = stringPreferencesKey("theme")
private val ONLY_NODES_IN_SCAN = booleanPreferencesKey("only_nodes_in_scan")

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        scanDuration = ScanDuration.entries.find { it.duration.inWholeSeconds.toInt() == this[SCAN_DURATION_SECONDS] }
            ?: defaults.scanDuration,
        theme = ThemeMode.entries.find { it.name == this[THEME] } ?: defaults.theme,
        onlyNodesInScan = this[ONLY_NODES_IN_SCAN] ?: defaults.onlyNodesInScan,
    )
}

private fun MutablePreferences.write(settings: AppSettings) {
    this[SCAN_DURATION_SECONDS] = settings.scanDuration.duration.inWholeSeconds.toInt()
    this[THEME] = settings.theme.name
    this[ONLY_NODES_IN_SCAN] = settings.onlyNodesInScan
}
