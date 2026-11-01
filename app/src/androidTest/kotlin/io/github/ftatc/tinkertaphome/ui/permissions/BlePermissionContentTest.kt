package io.github.ftatc.tinkertaphome.ui.permissions

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlePermissionContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val permissions = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
    private var requested: List<String>? = null
    private var settingsOpened = false

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(uiState: BlePermissionUiState) {
        composeRule.setContent {
            TinkerTapTheme {
                BlePermissionContent(
                    uiState = uiState,
                    onRequestPermissions = { requested = it },
                    onOpenSettings = { settingsOpened = true },
                ) {
                    Text(PROTECTED_CONTENT)
                }
            }
        }
    }

    @Test
    fun grantedShowsTheProtectedContent() {
        show(BlePermissionUiState.Granted)

        composeRule.onNodeWithText(PROTECTED_CONTENT).assertIsDisplayed()
    }

    @Test
    fun requiredShowsRationaleAndRequestsThePermissionsOnClick() {
        show(BlePermissionUiState.Required(permissions, previouslyDenied = false))

        composeRule.onNodeWithText(PROTECTED_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.permission_ble_rationale)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_ble_grant)).performClick()

        assertEquals(permissions, requested)
    }

    @Test
    fun requiredAfterDenialShowsTheDenialExplanation() {
        show(BlePermissionUiState.Required(permissions, previouslyDenied = true))

        composeRule.onNodeWithText(string(R.string.permission_ble_rationale_denied)).assertIsDisplayed()
    }

    @Test
    fun permanentlyDeniedOpensSettingsOnClick() {
        show(BlePermissionUiState.PermanentlyDenied)

        composeRule.onNodeWithText(PROTECTED_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.permission_ble_permanently_denied)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_ble_open_settings)).performClick()

        assertTrue(settingsOpened)
    }

    private companion object {
        const val PROTECTED_CONTENT = "Skyddat innehåll"
    }
}
