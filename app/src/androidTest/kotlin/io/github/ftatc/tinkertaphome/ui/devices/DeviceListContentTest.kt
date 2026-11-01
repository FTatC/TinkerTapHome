package io.github.ftatc.tinkertaphome.ui.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(uiState: DeviceListUiState) {
        composeRule.setContent {
            TinkerTapTheme { DeviceListContent(uiState = uiState) }
        }
    }

    @Test
    fun loadingStateShowsProgressIndicator() {
        show(DeviceListUiState.Loading)

        composeRule.onNodeWithContentDescription(string(R.string.devices_loading)).assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsEmptyMessage() {
        show(DeviceListUiState.Empty)

        composeRule.onNodeWithText(string(R.string.devices_empty)).assertIsDisplayed()
    }

    @Test
    fun contentStateShowsNameAndAddressOfEachDevice() {
        show(
            DeviceListUiState.Content(
                listOf(
                    Device(id = 1, address = "AA:BB:CC:DD:EE:01", name = "Lampa"),
                    Device(id = 2, address = "AA:BB:CC:DD:EE:02", name = "Relä"),
                ),
            ),
        )

        composeRule.onNodeWithText("Lampa").assertIsDisplayed()
        composeRule.onNodeWithText("AA:BB:CC:DD:EE:01").assertIsDisplayed()
        composeRule.onNodeWithText("Relä").assertIsDisplayed()
        composeRule.onNodeWithText("AA:BB:CC:DD:EE:02").assertIsDisplayed()
    }
}
