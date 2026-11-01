package io.github.ftatc.tinkertaphome.ui.save

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveDeviceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nameChanges = mutableListOf<String>()
    private var confirmed = false
    private var dismissed = false

    private fun show(uiState: SaveDeviceUiState) {
        composeRule.setContent {
            TinkerTapTheme {
                SaveDeviceDialog(
                    uiState = uiState,
                    onNameChange = { nameChanges += it },
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
    }

    @Test
    fun showsTitleAddressAndNameAndReportsActions() {
        show(SaveDeviceUiState(address = testAddress(1), name = "Lampa"))

        composeRule.onNodeWithText(context.getString(R.string.save_device_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testAddress(1).value).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.save_device_name_label)).performTextInput("!")
        composeRule.onNodeWithText(context.getString(R.string.save_device_confirm)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.save_device_cancel)).performClick()

        assertTrue(nameChanges.isNotEmpty())
        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun saveButtonIsDisabledWhileSaving() {
        show(SaveDeviceUiState(address = testAddress(1), name = "Lampa", isSaving = true))

        composeRule.onNodeWithText(context.getString(R.string.save_device_confirm)).assertIsNotEnabled()
    }

    @Test
    fun showsAlreadySavedError() {
        show(SaveDeviceUiState(address = testAddress(1), name = "Lampa", saveError = SaveDeviceError.AlreadySaved))

        composeRule.onNodeWithText(context.getString(R.string.save_device_error_already_saved)).assertIsDisplayed()
    }

    @Test
    fun showsTheMaximumLengthWhenTheNameIsTooLong() {
        show(SaveDeviceUiState(address = testAddress(1), name = "a", saveError = SaveDeviceError.NameTooLong))

        val message = context.resources.getQuantityString(
            R.plurals.save_device_error_name_too_long,
            DeviceNames.MAX_LENGTH,
            DeviceNames.MAX_LENGTH,
        )
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }
}
