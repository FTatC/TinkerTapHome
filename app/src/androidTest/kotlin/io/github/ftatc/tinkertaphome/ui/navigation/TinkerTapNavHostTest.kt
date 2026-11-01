package io.github.ftatc.tinkertaphome.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Testar navigationen med enkla platshållare i stället för de riktiga skärmarna och deras ViewModels. */
@RunWith(AndroidJUnit4::class)
class TinkerTapNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun showNavHost() {
        composeRule.setContent {
            TinkerTapNavHost(
                deviceListScreen = { onScanClick ->
                    Button(onClick = onScanClick) { Text(DEVICE_LIST) }
                },
                scanScreen = { Text(SCAN) },
            )
        }
    }

    @Test
    fun startsOnTheDeviceList() {
        showNavHost()

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
    }

    @Test
    fun scanClickOpensTheScanScreen() {
        showNavHost()

        composeRule.onNodeWithText(DEVICE_LIST).performClick()

        composeRule.onNodeWithText(SCAN).assertIsDisplayed()
    }

    @Test
    fun backFromTheScanScreenReturnsToTheDeviceList() {
        showNavHost()
        composeRule.onNodeWithText(DEVICE_LIST).performClick()
        // Vänta tills navigeringen är klar. Annars är NavHosts bakåthanterare inte registrerad än
        // och bakåt stänger aktiviteten i stället.
        composeRule.onNodeWithText(SCAN).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(DEVICE_LIST).assertIsDisplayed()
        composeRule.onNodeWithText(SCAN).assertDoesNotExist()
    }

    private companion object {
        const val DEVICE_LIST = "Enhetslista"
        const val SCAN = "Sökskärm"
    }
}
