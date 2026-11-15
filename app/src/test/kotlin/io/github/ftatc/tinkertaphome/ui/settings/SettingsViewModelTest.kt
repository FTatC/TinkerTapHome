package io.github.ftatc.tinkertaphome.ui.settings

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.repository.FakeSettingsRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveSettingsUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.UpdateSettingsUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    private fun viewModel() = SettingsViewModel(ObserveSettingsUseCase(repository), UpdateSettingsUseCase(repository))

    @Test
    fun `shows the saved settings`() = runTest {
        repository.settings.value = AppSettings(ScanDuration.SHORT, ThemeMode.DARK, onlyNodesInScan = true)

        viewModel().uiState.test {
            assertEquals(SettingsUiState(AppSettings(ScanDuration.SHORT, ThemeMode.DARK, onlyNodesInScan = true)), awaitItem())
        }
    }

    @Test
    fun `saves each change right away`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onScanDurationChange(ScanDuration.LONG)
            viewModel.onThemeChange(ThemeMode.LIGHT)
            viewModel.onOnlyNodesInScanChange(true)

            assertEquals(
                AppSettings(ScanDuration.LONG, ThemeMode.LIGHT, onlyNodesInScan = true),
                expectMostRecentItem().settings,
            )
        }
        assertEquals(AppSettings(ScanDuration.LONG, ThemeMode.LIGHT, onlyNodesInScan = true), repository.settings.value)
    }

    @Test
    fun `shows when a change cannot be saved`() = runTest {
        repository.updateError = SettingsError.StorageUnavailable
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onThemeChange(ThemeMode.DARK)

            val shown = awaitItem()
            assertTrue(shown.saveFailed)
            assertEquals(ThemeMode.SYSTEM, shown.settings.theme)
        }
    }

    @Test
    fun `clears the error when a later change is saved`() = runTest {
        repository.updateError = SettingsError.StorageUnavailable
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onThemeChange(ThemeMode.DARK)
            awaitItem()

            repository.updateError = null
            viewModel.onThemeChange(ThemeMode.DARK)

            assertFalse(expectMostRecentItem().saveFailed)
        }
    }
}
