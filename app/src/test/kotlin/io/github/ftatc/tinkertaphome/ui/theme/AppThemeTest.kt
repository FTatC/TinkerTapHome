package io.github.ftatc.tinkertaphome.ui.theme

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.domain.repository.FakeSettingsRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveSettingsUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppThemeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `follows the system by default`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun `a chosen theme ignores the system`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `the view model follows the chosen theme`() = runTest {
        val repository = FakeSettingsRepository(AppSettings(theme = ThemeMode.DARK))
        val viewModel = AppThemeViewModel(ObserveSettingsUseCase(repository))

        viewModel.theme.test {
            assertEquals(ThemeMode.DARK, awaitItem())

            repository.settings.value = AppSettings(theme = ThemeMode.LIGHT)

            assertEquals(ThemeMode.LIGHT, awaitItem())
        }
    }
}
