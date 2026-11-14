package io.github.ftatc.tinkertaphome.ui.save

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeError
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme
import org.junit.Assert.assertEquals
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
    private val tokenChanges = mutableListOf<String>()
    private var pairs = 0
    private var confirms = 0
    private var dismisses = 0

    private val start = SaveDeviceUiState(address = testAddress(1), name = "Lampa")
    private val details = start.copy(step = SaveStep.Details, info = testNodeInfo(authRequired = true))

    private fun show(uiState: SaveDeviceUiState) {
        composeRule.setContent {
            TinkerTapTheme {
                SaveDeviceDialog(
                    uiState = uiState,
                    actions = SaveDeviceActions(
                        onPair = { pairs++ },
                        onNameChange = { nameChanges += it },
                        onTokenChange = { tokenChanges += it },
                        onConfirm = { confirms++ },
                        onDismiss = { dismisses++ },
                    ),
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun firstAsksForThePairingButton() {
        show(start)

        composeRule.onNodeWithText(text(R.string.save_device_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testAddress(1).value).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.save_node_press_button)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.save_node_pair)).performClick()

        assertEquals(1, pairs)
    }

    @Test
    fun cancelIsReported() {
        show(start)

        composeRule.onNodeWithText(text(R.string.save_device_cancel)).performClick()

        assertEquals(1, dismisses)
    }

    @Test
    fun aFailedBondExplainsTheButton() {
        show(start.copy(pairError = NodeFailure.Connection(ConnectionError.BondFailed)))

        composeRule.onNodeWithText(text(R.string.save_node_error_bond_failed)).assertIsDisplayed()
    }

    @Test
    fun aNodeWithAnOlderProtocolAsksForAFirmwareUpdate() {
        show(start.copy(pairError = NodeFailure.Node(NodeError.NodeTooOld(2))))

        composeRule.onNodeWithText(text(R.string.node_error_node_too_old_version, 2)).assertIsDisplayed()
    }

    @Test
    fun showsProgressWhilePairing() {
        show(start.copy(step = SaveStep.Pairing))

        composeRule.onNodeWithText(text(R.string.save_node_pairing)).assertIsDisplayed()
    }

    @Test
    fun detailsShowNameAndTokenAndReportChanges() {
        show(details)

        composeRule.onNodeWithText(text(R.string.save_device_name_label)).performTextInput("!")
        composeRule.onNodeWithText(text(R.string.save_node_token_label)).performTextInput("x")

        assertTrue(nameChanges.isNotEmpty())
        assertTrue(tokenChanges.isNotEmpty())
    }

    @Test
    fun confirmIsReported() {
        show(details)

        composeRule.onNodeWithText(text(R.string.save_device_confirm)).performClick()

        assertEquals(1, confirms)
    }

    @Test
    fun theTokenIsNotShownInPlainText() {
        show(details.copy(token = "hemligt"))

        // InputText innehåller alltid det riktiga värdet. Det som visas på skärmen är EditableText.
        val tokenField = composeRule
            .onNode(hasText(text(R.string.save_node_token_label), substring = true) and hasSetTextAction())
            .fetchSemanticsNode()
        assertEquals("•".repeat("hemligt".length), tokenField.config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun theTokenFieldIsHiddenWhenTheNodeNeedsNoToken() {
        show(details.copy(info = testNodeInfo(authRequired = false)))

        composeRule.onNodeWithText(text(R.string.save_node_token_label)).assertDoesNotExist()
    }

    @Test
    fun aWrongTokenIsShownAtTheTokenField() {
        show(details.copy(saveError = SaveNodeError.Node(NodeFailure.Command(CommandError.AuthFailed))))

        composeRule.onNodeWithText(text(R.string.node_error_auth_failed)).assertIsDisplayed()
    }

    @Test
    fun anInvalidTokenIsExplained() {
        show(details.copy(tokenInvalid = true))

        composeRule.onNodeWithText(text(R.string.save_node_error_token_invalid, AuthToken.MAX_LENGTH)).assertIsDisplayed()
    }

    @Test
    fun showsNameTooLongError() {
        show(details.copy(saveError = SaveNodeError.Save(SaveDeviceError.NameTooLong)))

        val message = context.resources.getQuantityString(
            R.plurals.save_device_error_name_too_long,
            DeviceNames.MAX_LENGTH,
            DeviceNames.MAX_LENGTH,
        )
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun showsAlreadySavedError() {
        show(details.copy(saveError = SaveNodeError.Save(SaveDeviceError.AlreadySaved)))

        composeRule.onNodeWithText(text(R.string.save_device_error_already_saved)).assertIsDisplayed()
    }

    @Test
    fun saveIsDisabledWhileSaving() {
        show(details.copy(step = SaveStep.Saving))

        composeRule.onNodeWithText(text(R.string.save_device_confirm)).assertIsNotEnabled()
    }
}
