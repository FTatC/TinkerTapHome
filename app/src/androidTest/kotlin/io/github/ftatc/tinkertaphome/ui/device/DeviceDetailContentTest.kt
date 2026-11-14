package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeOutputs
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import io.github.ftatc.tinkertaphome.domain.usecase.RepairNodeError
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
    private var allOffClicks = 0
    private var tokenSubmits = 0
    private var repairClicks = 0
    private val outputChanges = mutableListOf<Pair<Output, Int>>()
    private val typedTokens = mutableListOf<String>()

    private val led = Output(OutputId(0), "led", OutputType.Digital)
    private val fan = Output(OutputId(1), "fan", OutputType.Pwm)
    private val servo = Output(OutputId(2), "servo", OutputType.Servo)
    private val motor = Output(OutputId(3), "motor", OutputType.Unknown("M"))
    private val outputs = NodeOutputs(testNodeInfo(outputs = listOf(led, fan, servo, motor)), listOf(1, 128, 90, 0))

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(uiState: DeviceDetailUiState) {
        composeRule.setContent {
            TinkerTapTheme {
                DeviceDetailContent(
                    uiState = uiState,
                    actions = DeviceDetailActions(
                        onRetryClick = { retryClicks++ },
                        onOutputChange = { output, value -> outputChanges += output to value },
                        onAllOffClick = { allOffClicks++ },
                        onTokenChange = { typedTokens += it },
                        onTokenSubmit = { tokenSubmits++ },
                        onRepairClick = { repairClicks++ },
                    ),
                )
            }
        }
    }

    private fun showDevice(connection: ConnectionState) = show(DeviceDetailUiState.Content(lampa, connection))

    private fun showSession(session: NodeSession, connection: ConnectionState = ConnectionState.Connected) =
        show(DeviceDetailUiState.Content(lampa, connection, session = session))

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

    @Test
    fun showsTheOutputsWithTheirValues() {
        showSession(NodeSession.Open(outputs))

        composeRule.onNodeWithText(string(R.string.device_firmware, "0.4.0")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("led").assertIsOn()
        composeRule.onNodeWithText(string(R.string.device_output_percent, 50)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_output_degrees, 90)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_output_unknown)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_last_known)).assertDoesNotExist()
    }

    @Test
    fun switchingADigitalOutputSendsTheNewValue() {
        showSession(NodeSession.Open(NodeOutputs(testNodeInfo(outputs = listOf(led)), listOf(0))))

        composeRule.onNodeWithContentDescription("led").assertIsOff().performClick()

        assertEquals(listOf(led to 1), outputChanges)
    }

    @Test
    fun releasingASliderSendsItsValue() {
        showSession(NodeSession.Open(outputs))

        composeRule.onNodeWithContentDescription("fan").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }

        assertEquals(listOf(fan to 200), outputChanges)
    }

    @Test
    fun allOffCanBeSent() {
        showSession(NodeSession.Open(outputs))

        composeRule.onNodeWithText(string(R.string.device_all_off)).performScrollTo().performClick()

        assertEquals(1, allOffClicks)
    }

    @Test
    fun theLastKnownValuesCannotBeChanged() {
        showSession(NodeSession.Closed(outputs), connection = ConnectionState.Waiting)

        composeRule.onNodeWithText(string(R.string.device_last_known)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("led").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("fan").assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.device_all_off)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun controlsWaitWhileACommandIsSent() {
        show(DeviceDetailUiState.Content(lampa, ConnectionState.Connected, session = NodeSession.Open(outputs), isSending = true))

        composeRule.onNodeWithContentDescription("led").assertIsNotEnabled()
    }

    @Test
    fun showsThatTheAppLogsIn() {
        showSession(NodeSession.Opening())

        composeRule.onNodeWithText(string(R.string.device_session_opening)).assertIsDisplayed()
    }

    @Test
    fun aRejectedCommandIsShown() {
        show(
            DeviceDetailUiState.Content(
                lampa,
                ConnectionState.Connected,
                session = NodeSession.Open(outputs),
                commandError = NodeFailure.Command(CommandError.InvalidOutput),
            ),
        )

        composeRule.onNodeWithText(string(R.string.node_error_invalid_output)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aWrongTokenAsksForANewOne() {
        showSession(
            NodeSession.Failed(NodeSessionError.Node(NodeFailure.Command(CommandError.AuthFailed))),
            connection = ConnectionState.Disconnected,
        )

        composeRule.onNodeWithText(string(R.string.node_error_auth_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.save_node_token_label)).performTextInput("nytt")
        composeRule.onNodeWithText(string(R.string.device_token_save)).performClick()

        assertEquals(listOf("nytt"), typedTokens)
        assertEquals(1, tokenSubmits)
    }

    @Test
    fun aMissingTokenAsksForOne() {
        showSession(NodeSession.Failed(NodeSessionError.TokenMissing), connection = ConnectionState.Disconnected)

        composeRule.onNodeWithText(string(R.string.device_error_token_missing)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_token_save)).assertIsDisplayed()
    }

    @Test
    fun otherSessionErrorsDoNotAskForAToken() {
        showSession(
            NodeSession.Failed(NodeSessionError.Node(NodeFailure.Connection(ConnectionError.PairingMissing))),
            connection = ConnectionState.Disconnected,
        )

        composeRule.onNodeWithText(string(R.string.connection_error_pairing_missing)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_token_save)).assertDoesNotExist()
    }

    @Test
    fun aMissingPairingOffersToPairAgainInsteadOfARetry() {
        showDevice(ConnectionState.Failed(ConnectionError.PairingMissing))

        composeRule.onNodeWithText(string(R.string.device_repair_instructions)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_retry)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.device_repair)).performClick()

        assertEquals(1, repairClicks)
    }

    @Test
    fun aForgottenPairingOffersToPairAgain() {
        showSession(
            NodeSession.Failed(NodeSessionError.Node(NodeFailure.Connection(ConnectionError.PairingMissing))),
            connection = ConnectionState.Disconnected,
        )

        composeRule.onNodeWithText(string(R.string.device_repair)).assertIsDisplayed()
    }

    @Test
    fun theButtonWaitsWhilePairing() {
        show(DeviceDetailUiState.Content(lampa, ConnectionState.Connecting, isRepairing = true))

        // Under parkopplingen är anslutningen inte längre misslyckad, men formuläret ska ändå synas.
        composeRule.onNodeWithText(string(R.string.save_node_pairing)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.device_repair)).assertIsNotEnabled()
    }

    @Test
    fun aPairingThatAndroidStillHasIsExplained() {
        show(
            DeviceDetailUiState.Content(
                lampa,
                ConnectionState.Failed(ConnectionError.PairingMissing),
                repairError = RepairNodeError.StillPairedInAndroid,
            ),
        )

        composeRule.onNodeWithText(string(R.string.device_repair_error_still_paired)).assertIsDisplayed()
    }

    @Test
    fun aFailedPairingAsksForTheButtonOnTheDevice() {
        show(
            DeviceDetailUiState.Content(
                lampa,
                ConnectionState.Failed(ConnectionError.PairingMissing),
                repairError = RepairNodeError.Connection(ConnectionError.BondFailed),
            ),
        )

        composeRule.onNodeWithText(string(R.string.save_node_error_bond_failed)).assertIsDisplayed()
    }
}
