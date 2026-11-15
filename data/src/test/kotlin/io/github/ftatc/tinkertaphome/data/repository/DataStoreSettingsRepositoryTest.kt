package io.github.ftatc.tinkertaphome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }

    @Test
    fun `gives the defaults when nothing is saved`() = runTest {
        assertEquals(AppSettings(), DataStoreSettingsRepository(dataStore()).observeSettings().first())
    }

    @Test
    fun `saves and reads every setting`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        val changed = AppSettings(ScanDuration.LONG, ThemeMode.DARK, onlyNodesInScan = true)

        assertEquals(Outcome.Success(Unit), repository.update { changed })

        assertEquals(changed, repository.observeSettings().first())
    }

    @Test
    fun `sends the new settings when they change`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())

        repository.observeSettings().test {
            assertEquals(AppSettings(), awaitItem())

            repository.update { it.copy(theme = ThemeMode.LIGHT) }

            assertEquals(AppSettings(theme = ThemeMode.LIGHT), awaitItem())
        }
    }

    @Test
    fun `gives the defaults for values it does not know`() = runTest {
        val store = dataStore()
        store.edit {
            it[intPreferencesKey("scan_duration_seconds")] = 45
            it[stringPreferencesKey("theme")] = "SEPIA"
        }

        assertEquals(AppSettings(), DataStoreSettingsRepository(store).observeSettings().first())
    }

    @Test
    fun `gives the defaults when the settings cannot be read`() = runTest {
        assertEquals(AppSettings(), DataStoreSettingsRepository(FailingDataStore).observeSettings().first())
    }

    @Test
    fun `reports when the settings cannot be saved`() = runTest {
        assertEquals(
            Outcome.Failure(SettingsError.StorageUnavailable),
            DataStoreSettingsRepository(FailingDataStore).update { it.copy(theme = ThemeMode.DARK) },
        )
    }

    /** En DataStore vars fil inte går att läsa eller skriva. */
    private object FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disken är full") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disken är full")
    }
}
