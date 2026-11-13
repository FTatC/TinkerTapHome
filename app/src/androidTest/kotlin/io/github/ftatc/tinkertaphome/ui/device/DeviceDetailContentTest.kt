package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lampa = testDevice(id = 1, name = "Lampa")
    private var retryClicks = 0

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(uiState: DeviceDetailUiState) {
        composeRule.setContent {
            TinkerTapTheme {
                DeviceDetailContent(uiState = uiState, onRetryClick = { retryClicks++ })
            }
        }
    }

    private fun showDevice(connection: ConnectionState) = show(DeviceDetailUiState.Content(lampa, connection))

    @Test
    fun loadingStateShowsProgressIndicator() {
        show(DeviceDetailUiState.Loading)

        composeRule.onNodeWithContentDescription(string(R.string.device_loading)).assertIsDisplayed()
    }

    @Test
    fun notFoundStateShowsMessage() {
        show(DeviceDetailUiState.NotFound)

        composeRule.onNodeWithText(string(R.string.device_not_found)).assertIsDisplayed()
    }

    @Test
    fun errorStateShowsStorageErrorMessage() {
        show(DeviceDetailUiState.Error(DeviceError.StorageUnavailable))

        composeRule.onNodeWithText(string(R.string.devices_error_storage)).assertIsDisplayed()
    }

    @Test
    fun showsNameAddressAndState() {
        showDevice(ConnectionState.Disconnected)

        composeRule.onNodeWithText("Lampa").assertIsDisplayed()
        composeRule.onNodeWithText(lampa.address.value).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connection_disconnected)).assertIsDisplayed()
    }

    @Test
    fun showsThatTheAppIsWaitingForTheDevice() {
        showDevice(ConnectionState.Waiting)

        composeRule.onNodeWithText(string(R.string.connection_waiting)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_retry)).assertDoesNotExist()
    }

    @Test
    fun aConnectedDeviceHasNoButtons() {
        showDevice(ConnectionState.Connected)

        composeRule.onNodeWithText(string(R.string.connection_connected)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_retry)).assertDoesNotExist()
    }

    @Test
    fun aFailedConnectionShowsTheErrorAndCanBeRetried() {
        showDevice(ConnectionState.Failed(ConnectionError.ConnectFailed(133)))

        composeRule.onNodeWithText(string(R.string.connection_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connection_error_connect_failed, 133)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_retry)).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun aMissingPairingTellsTheUserToPairAgain() {
        showDevice(ConnectionState.Failed(ConnectionError.PairingMissing))

        composeRule.onNodeWithText(string(R.string.connection_error_pairing_missing)).assertIsDisplayed()
    }

    @Test
    fun aMissingReplyIsShown() {
        showDevice(ConnectionState.Failed(ConnectionError.NoReply))

        composeRule.onNodeWithText(string(R.string.connection_error_no_reply)).assertIsDisplayed()
    }

    @Test
    fun bluetoothTurnedOffIsShown() {
        showDevice(ConnectionState.Failed(ConnectionError.BluetoothOff))

        composeRule.onNodeWithText(string(R.string.connection_error_bluetooth_off)).assertIsDisplayed()
    }
}
