package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.repository.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUseCasesTest {

    private val repository = FakeSettingsRepository()
    private val observe = ObserveSettingsUseCase(repository)
    private val update = UpdateSettingsUseCase(repository)

    @Test
    fun `the defaults behave as before the settings existed`() {
        assertEquals(AppSettings(ScanDuration.NORMAL, ThemeMode.SYSTEM, onlyNodesInScan = false), AppSettings())
        assertEquals(30, ScanDuration.DEFAULT.duration.inWholeSeconds)
    }

    @Test
    fun `follows changes to the settings`() = runTest {
        observe().test {
            assertEquals(AppSettings(), awaitItem())

            update.setTheme(ThemeMode.DARK)

            assertEquals(AppSettings(theme = ThemeMode.DARK), awaitItem())
        }
    }

    @Test
    fun `does not repeat unchanged settings`() = runTest {
        observe().test {
            awaitItem()

            update.setTheme(ThemeMode.SYSTEM)

            expectNoEvents()
        }
    }

    @Test
    fun `changes one setting at a time`() = runTest {
        update.setScanDuration(ScanDuration.LONG)
        update.setOnlyNodesInScan(true)

        assertEquals(AppSettings(scanDuration = ScanDuration.LONG, onlyNodesInScan = true), repository.settings.value)
    }

    @Test
    fun `reports when the settings cannot be saved`() = runTest {
        repository.updateError = SettingsError.StorageUnavailable

        assertEquals(Outcome.Failure(SettingsError.StorageUnavailable), update.setTheme(ThemeMode.LIGHT))
        assertEquals(AppSettings(), repository.settings.value)
    }
}
