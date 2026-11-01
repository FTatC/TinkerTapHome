package io.github.ftatc.tinkertaphome.ui.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var scanAgainClicked = false

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(uiState: ScanUiState) {
        composeRule.setContent {
            TinkerTapTheme { ScanContent(uiState = uiState, onScanAgain = { scanAgainClicked = true }) }
        }
    }

    @Test
    fun scanningWithoutResultsShowsProgressAndSearchingText() {
        show(ScanUiState(isScanning = true))

        composeRule.onNodeWithContentDescription(string(R.string.scan_in_progress)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.scan_searching)).assertIsDisplayed()
    }

    @Test
    fun foundDevicesShowNameAddressAndSignal() {
        val device = testScannedDevice(id = 1, name = "Lampa", rssi = -48)

        show(ScanUiState(devices = listOf(device), isScanning = true))

        composeRule.onNodeWithText("Lampa").assertIsDisplayed()
        composeRule.onNodeWithText(device.address.value).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.scan_rssi, -48)).assertIsDisplayed()
    }

    @Test
    fun deviceWithoutNameIsShownAsUnknown() {
        show(ScanUiState(devices = listOf(testScannedDevice(id = 1, name = null)), isScanning = true))

        composeRule.onNodeWithText(string(R.string.scan_unknown_device)).assertIsDisplayed()
    }

    @Test
    fun finishedWithoutResultsShowsHintAndSearchAgain() {
        show(ScanUiState(isScanning = false))

        composeRule.onNodeWithText(string(R.string.scan_none_found)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.scan_again)).performClick()

        assertTrue(scanAgainClicked)
    }

    @Test
    fun bluetoothOffShowsMessageAndRetry() {
        show(ScanUiState(error = BleError.BluetoothOff))

        composeRule.onNodeWithText(string(R.string.scan_error_bluetooth_off)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.scan_retry)).performClick()

        assertTrue(scanAgainClicked)
    }

    @Test
    fun scanFailureShowsTheErrorCode() {
        show(ScanUiState(error = BleError.ScanFailed(code = 2)))

        composeRule.onNodeWithText(string(R.string.scan_error_failed, 2)).assertIsDisplayed()
    }
}
