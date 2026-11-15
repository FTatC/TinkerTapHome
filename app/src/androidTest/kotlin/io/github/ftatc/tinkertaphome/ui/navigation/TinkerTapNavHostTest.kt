package io.github.ftatc.tinkertaphome.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ftatc.tinkertaphome.domain.model.testScannedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Testar navigationen med enkla platshållare i stället för de riktiga skärmarna och deras ViewModels. */
@RunWith(AndroidJUnit4::class)
class TinkerTapNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val device = testScannedDevice(id = 1, name = "Lampa")
    private var openedRoute: SaveDeviceRoute? = null
    private var openedDetail: DeviceDetailRoute? = null
    private var scanLifecycle: Lifecycle.State? = null

    private fun showNavHost() {
        composeRule.setContent {
            TinkerTapNavHost(
                deviceListScreen = { onScanClick, onDeviceClick, onSettingsClick ->
                    Column {
                        Button(onClick = onScanClick) { Text(DEVICE_LIST) }
                        Button(onClick = { onDeviceClick(DEVICE_ID) }) { Text(SAVED_DEVICE) }
                        Button(onClick = onSettingsClick) { Text(OPEN_SETTINGS) }
                    }
                },
                settingsScreen = { Text(SETTINGS) },
                deviceDetailScreen = { route ->
                    openedDetail = route
                    Text(DETAIL)
                },
                scanScreen = { onDeviceClick ->
                    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
                    scanLifecycle = state
                    Button(onClick = { onDeviceClick(device) }) { Text(SCAN) }
                },
                saveDeviceScreen = { route, onSaved, onDismiss ->
                    openedRoute = route
                    Column {
                        Button(onClick = onSaved) { Text(SAVE) }
                        Button(onClick = onDismiss) { Text(CANCEL) }
                    }
                },
            )
        }
    }

    private fun openScanScreen() {
        composeRule.onNodeWithText(DEVICE_LIST).performClick()
        // Vänta tills navigeringen är klar. Annars är NavHosts bakåthanterare inte registrerad än.
        composeRule.onNodeWithText(SCAN).assertIsDisplayed()
    }

    private fun openSaveDialog() {
        openScanScreen()
        composeRule.onNodeWithText(SCAN).performClick()
        composeRule.onNodeWithText(SAVE).assertIsDisplayed()
    }

    @Test
    fun startsOnTheDeviceList() {
        showNavHost()

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
    }

    @Test
    fun scanClickOpensTheScanScreen() {
        showNavHost()

        openScanScreen()
    }

    @Test
    fun backFromTheScanScreenReturnsToTheDeviceList() {
        showNavHost()
        openScanScreen()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
        composeRule.onNodeWithText(SCAN).assertDoesNotExist()
    }

    @Test
    fun theSettingsIconOpensTheSettingsAndBackReturns() {
        showNavHost()

        composeRule.onNodeWithText(OPEN_SETTINGS).performClick()
        composeRule.onNodeWithText(SETTINGS).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
    }

    @Test
    fun choosingASavedDeviceOpensItsDetailScreen() {
        showNavHost()

        composeRule.onNodeWithText(SAVED_DEVICE).performClick()

        composeRule.onNodeWithText(DETAIL).assertIsDisplayed()
        assertEquals(DeviceDetailRoute(DEVICE_ID), openedDetail)
    }

    @Test
    fun backFromTheDetailScreenReturnsToTheDeviceList() {
        showNavHost()
        composeRule.onNodeWithText(SAVED_DEVICE).performClick()
        composeRule.onNodeWithText(DETAIL).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
        composeRule.onNodeWithText(DETAIL).assertDoesNotExist()
    }

    @Test
    fun choosingADeviceOpensTheSaveDialogWithItsAddressAndName() {
        showNavHost()

        openSaveDialog()

        assertEquals(SaveDeviceRoute(address = device.address.value, suggestedName = "Lampa"), openedRoute)
    }

    @Test
    fun theScanScreenKeepsRunningBehindTheSaveDialog() {
        // Skanningen stoppas när skärmen inte är startad. Dialogen ligger ovanpå, så skärmen ska
        // fortsätta vara startad och skanningen pågå medan användaren skriver ett namn.
        showNavHost()

        openSaveDialog()

        assertTrue(checkNotNull(scanLifecycle).isAtLeast(Lifecycle.State.STARTED))
    }

    @Test
    fun savingReturnsToTheDeviceList() {
        showNavHost()
        openSaveDialog()

        composeRule.onNodeWithText(SAVE).performClick()

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
        composeRule.onNodeWithText(SCAN).assertDoesNotExist()
        composeRule.onNodeWithText(SAVE).assertDoesNotExist()
    }

    @Test
    fun cancellingReturnsToTheScanScreen() {
        showNavHost()
        openSaveDialog()

        composeRule.onNodeWithText(CANCEL).performClick()

        composeRule.onNodeWithText(SCAN).assertIsDisplayed()
        composeRule.onNodeWithText(SAVE).assertDoesNotExist()
    }

    private companion object {
        const val DEVICE_LIST = "Enhetslista"
        const val SAVED_DEVICE = "Sparad enhet"
        const val DETAIL = "Detaljskärm"
        const val DEVICE_ID = 7L
        const val SCAN = "Sökskärm"
        const val SAVE = "Spara"
        const val CANCEL = "Avbryt"
        const val OPEN_SETTINGS = "Öppna inställningar"
        const val SETTINGS = "Inställningsskärm"
    }
}
