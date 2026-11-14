package io.github.ftatc.tinkertaphome.ui.save

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeClient
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.PairNodeUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.SaveDeviceUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeError
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import io.github.ftatc.tinkertaphome.ui.navigation.SaveDeviceRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SaveDeviceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val address = testAddress(1)
    private val repository = FakeDeviceRepository()
    private val connection = FakeDeviceConnection()
    private val nodeClient = FakeNodeClient()

    private fun viewModel(suggestedName: String? = "TinkerTapNode-A1B2") = SaveDeviceViewModel(
        route = SaveDeviceRoute(address.value, suggestedName),
        pairNode = PairNodeUseCase(connection, nodeClient),
        saveNode = SaveNodeUseCase(SaveDeviceUseCase(repository), connection, nodeClient),
    )

    /** En ViewModel som har parkopplat och står på steget för namn och token. */
    private fun paired(): SaveDeviceViewModel = viewModel().also { it.onPairClick() }

    @Test
    fun `starts by asking for the pairing button with the advertised name and the default token`() {
        val state = viewModel().uiState.value

        assertEquals(SaveStep.PressButton, state.step)
        assertEquals("TinkerTapNode-A1B2", state.name)
        assertEquals("tinker", state.token)
    }

    @Test
    fun `a device without a name starts with an empty name`() {
        assertEquals("", viewModel(suggestedName = null).uiState.value.name)
    }

    @Test
    fun `pairing reads the info and moves on to name and token`() {
        val viewModel = paired()

        val state = viewModel.uiState.value
        assertEquals(SaveStep.Details, state.step)
        assertEquals(testNodeInfo(), state.info)
        assertTrue(state.showsTokenField)
        assertEquals(listOf(address), connection.pairCalls)
    }

    @Test
    fun `the token field is hidden when the node needs no authentication`() {
        nodeClient.info = Outcome.Success(testNodeInfo(authRequired = false))

        assertFalse(paired().uiState.value.showsTokenField)
    }

    @Test
    fun `a failed pairing goes back to the button step and shows the error`() {
        connection.connectError = ConnectionError.BondFailed

        val state = paired().uiState.value

        assertEquals(SaveStep.PressButton, state.step)
        assertEquals(NodeFailure.Connection(ConnectionError.BondFailed), state.pairError)
    }

    @Test
    fun `pairing again clears the previous error`() {
        connection.connectError = ConnectionError.BondFailed
        val viewModel = paired()
        connection.connectError = null

        viewModel.onPairClick()

        assertNull(viewModel.uiState.value.pairError)
        assertEquals(SaveStep.Details, viewModel.uiState.value.step)
    }

    @Test
    fun `saving authenticates and stores the node with its name and token`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = paired()
        viewModel.onNameChange("Lampa i köket")

        viewModel.onSaveConfirm()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(listOf(testDevice(id = 1, name = "Lampa i köket")), repository.devices.value)
        assertEquals(AuthToken.DEFAULT, repository.tokens[address])
        assertEquals(address to NodeCommand.Authenticate(AuthToken.DEFAULT), nodeClient.sent.single())
    }

    @Test
    fun `an invalid token is shown without contacting the node`() {
        val viewModel = paired()
        viewModel.onTokenChange("")

        viewModel.onSaveConfirm()

        assertTrue(viewModel.uiState.value.tokenInvalid)
        assertTrue(nodeClient.sent.isEmpty())
    }

    @Test
    fun `a wrong token is shown and nothing is saved`() {
        nodeClient.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)) }
        val viewModel = paired()

        viewModel.onSaveConfirm()

        val state = viewModel.uiState.value
        assertEquals(SaveStep.Details, state.step)
        assertEquals(SaveNodeError.Node(NodeFailure.Command(CommandError.AuthFailed)), state.saveError)
        assertTrue(repository.devices.value.isEmpty())
    }

    @Test
    fun `changing the token clears the error`() {
        nodeClient.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.AuthFailed)) }
        val viewModel = paired()
        viewModel.onSaveConfirm()

        viewModel.onTokenChange("nytt")

        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun `a name error is shown`() {
        val viewModel = paired()
        viewModel.onNameChange(" ")

        viewModel.onSaveConfirm()

        assertEquals(SaveNodeError.Save(SaveDeviceError.NameMissing), viewModel.uiState.value.saveError)
    }

    @Test
    fun `saving does nothing before the node is paired`() {
        val viewModel = viewModel()

        viewModel.onSaveConfirm()

        assertTrue(repository.saveCalls.isEmpty())
        assertEquals(SaveStep.PressButton, viewModel.uiState.value.step)
    }
}
