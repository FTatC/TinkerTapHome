package io.github.ftatc.tinkertaphome.ui.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var scanClicked = false
    private var clickedDeviceId: Long? = null

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(uiState: DeviceListUiState) {
        composeRule.setContent {
            TinkerTapTheme { DeviceListContent(uiState = uiState, onScanClick = { scanClicked = true }, onDeviceClick = { clickedDeviceId = it }) }
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
    fun errorStateShowsStorageErrorMessage() {
        show(DeviceListUiState.Error(DeviceError.StorageUnavailable))

        composeRule.onNodeWithText(string(R.string.devices_error_storage)).assertIsDisplayed()
    }

    @Test
    fun contentStateShowsNameAndAddressOfEachDevice() {
        val devices = listOf(testDevice(id = 1, name = "Lampa"), testDevice(id = 2, name = "Relä"))

        show(DeviceListUiState.Content(devices.map { ConnectedDevice(it, ConnectionState.Disconnected) }))

        devices.forEach { device ->
            composeRule.onNodeWithText(device.name).assertIsDisplayed()
            composeRule.onNodeWithText(device.address.value).assertIsDisplayed()
        }
    }

    @Test
    fun contentStateShowsTheConnectionStateOfEachDevice() {
        show(
            DeviceListUiState.Content(
                listOf(
                    ConnectedDevice(testDevice(id = 1, name = "Lampa"), ConnectionState.Connected),
                    ConnectedDevice(testDevice(id = 2, name = "Relä"), ConnectionState.Connecting),
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.connection_connected)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connection_connecting)).assertIsDisplayed()
    }

    @Test
    fun clickingADevicePassesOnItsId() {
        show(DeviceListUiState.Content(listOf(ConnectedDevice(testDevice(id = 7, name = "Lampa"), ConnectionState.Disconnected))))

        composeRule.onNodeWithText("Lampa").performClick()

        assertEquals(7L, clickedDeviceId)
    }

    @Test
    fun findDevicesButtonCallsOnScanClick() {
        show(DeviceListUiState.Empty)

        composeRule.onNodeWithText(string(R.string.devices_scan)).performClick()

        assertTrue(scanClicked)
    }
}
