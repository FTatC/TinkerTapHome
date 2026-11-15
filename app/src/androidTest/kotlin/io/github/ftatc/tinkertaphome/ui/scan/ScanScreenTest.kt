package io.github.ftatc.tinkertaphome.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceScanner
import io.github.ftatc.tinkertaphome.domain.model.BleError
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.FakeSettingsRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ScanForDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testar kopplingarna i [ScanScreen]: att skanningen följer skärmens livscykel och att knappar och
 * val skickas vidare. Logiken i [ScanViewModel] och [ScanContent] testas separat.
 */
@RunWith(AndroidJUnit4::class)
class ScanScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scanner = FakeDeviceScanner()
    private val viewModel = ScanViewModel(ScanForDevicesUseCase(scanner, FakeDeviceRepository(), FakeSettingsRepository()))
    private var clickedDevice: ScannedDevice? = null

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun showScanScreen() {
        composeRule.setContent {
            TinkerTapTheme { ScanScreen(onDeviceClick = { clickedDevice = it }, viewModel = viewModel) }
        }
    }

    private fun emit(outcome: Outcome<List<ScannedDevice>, BleError>) = runBlocking { scanner.emit(outcome) }

    private fun waitUntilScanning(scanning: Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { scanner.isScanning == scanning }
    }

    @Test
    fun startsScanningWhenTheScreenIsShown() {
        emit(Outcome.Success(emptyList()))

        showScanScreen()

        waitUntilScanning(true)
        assertEquals(1, scanner.startCount)
    }

    @Test
    fun stopsScanningWhenTheAppGoesToTheBackgroundAndRestartsWhenItReturns() {
        emit(Outcome.Success(emptyList()))
        showScanScreen()
        waitUntilScanning(true)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitUntilScanning(false)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitUntilScanning(true)
        assertEquals(2, scanner.startCount)
    }

    @Test
    fun stopsScanningWhenTheScreenIsRemoved() {
        emit(Outcome.Success(emptyList()))
        var visible by mutableStateOf(true)
        composeRule.setContent {
            TinkerTapTheme {
                if (visible) ScanScreen(onDeviceClick = {}, viewModel = viewModel)
            }
        }
        waitUntilScanning(true)

        composeRule.runOnIdle { visible = false }

        waitUntilScanning(false)
    }

    @Test
    fun tryAgainStartsANewScan() {
        emit(Outcome.Failure(BleError.BluetoothOff))
        showScanScreen()
        composeRule.waitUntil(timeoutMillis = 5_000) { scanner.startCount == 1 && !scanner.isScanning }
        emit(Outcome.Success(emptyList()))

        composeRule.onNodeWithText(string(R.string.scan_retry)).performClick()

        waitUntilScanning(true)
        assertEquals(2, scanner.startCount)
    }

    @Test
    fun choosingAFoundDeviceIsPassedOn() {
        val device = testScannedDevice(id = 1, name = "Lampa")
        emit(Outcome.Success(listOf(device)))
        showScanScreen()

        composeRule.onNodeWithText("Lampa").performClick()

        assertEquals(device, clickedDevice)
    }
}
