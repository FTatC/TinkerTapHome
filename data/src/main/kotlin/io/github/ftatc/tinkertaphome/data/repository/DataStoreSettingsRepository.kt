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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Inställningarna i DataStore. Värdena sparas som enkla typer: skanningens längd i sekunder och
 * temat som namnet på [ThemeMode]. Ett okänt värde, till exempel från en senare version av appen,
 * ger standardvärdet i stället för ett fel.
 */
internal class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { it.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit, SettingsError> =
        try {
            dataStore.edit { preferences -> preferences.write(transform(preferences.toAppSettings())) }
            Outcome.Success(Unit)
        } catch (_: IOException) {
            Outcome.Failure(SettingsError.StorageUnavailable)
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
