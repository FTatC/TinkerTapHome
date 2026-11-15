package io.github.ftatc.tinkertaphome.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.ScanDuration
import io.github.ftatc.tinkertaphome.domain.model.ThemeMode
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val durations = mutableListOf<ScanDuration>()
    private val themes = mutableListOf<ThemeMode>()
    private val onlyNodes = mutableListOf<Boolean>()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(uiState: SettingsUiState = SettingsUiState()) {
        composeRule.setContent {
            TinkerTapTheme {
                SettingsContent(
                    uiState = uiState,
                    actions = SettingsActions(
                        onScanDurationChange = { durations += it },
                        onThemeChange = { themes += it },
                        onOnlyNodesInScanChange = { onlyNodes += it },
                    ),
                )
            }
        }
    }

    @Test
    fun showsTheSavedSettings() {
        show(SettingsUiState(AppSettings(ScanDuration.LONG, ThemeMode.DARK, onlyNodesInScan = true)))

        composeRule.onNodeWithText(string(R.string.settings_scan_seconds, 60)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.settings_only_nodes)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performScrollTo().assertIsSelected()
    }

    @Test
    fun choosingAScanDurationSendsIt() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_scan_seconds, 15)).performClick()

        assertEquals(listOf(ScanDuration.SHORT), durations)
    }

    @Test
    fun choosingAThemeSendsIt() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_light)).performScrollTo().performClick()

        assertEquals(listOf(ThemeMode.LIGHT), themes)
    }

    @Test
    fun theWholeRowSwitchesOnlyNodes() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_only_nodes)).performClick()

        assertEquals(listOf(true), onlyNodes)
    }

    @Test
    fun aFailedSaveIsShown() {
        show(SettingsUiState(saveFailed = true))

        composeRule.onNodeWithText(string(R.string.settings_save_failed)).performScrollTo().assertIsDisplayed()
    }
}
