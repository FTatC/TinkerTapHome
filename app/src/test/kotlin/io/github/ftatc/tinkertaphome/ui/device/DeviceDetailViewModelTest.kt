package io.github.ftatc.tinkertaphome.ui.device

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeOutputs
import io.github.ftatc.tinkertaphome.domain.node.NodeSession
import io.github.ftatc.tinkertaphome.domain.node.NodeSessionError
import io.github.ftatc.tinkertaphome.domain.node.testNodeInfo
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.AllOffUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveOutputsUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.RepairNodeError
import io.github.ftatc.tinkertaphome.domain.usecase.RepairNodeUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.RetryConnectionUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.SetOutputUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.UpdateAuthTokenUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val repository = FakeDeviceRepository(listOf(lampa))
    private val connection = FakeDeviceConnection()
    private val sessions = FakeNodeSessions(connection)
    private val retry = RetryConnectionUseCase(connection, sessions)

    private val info = testNodeInfo()
    private val led = info.outputs.single()
    private val open = NodeSession.Open(NodeOutputs(info, listOf(0)))

    private fun viewModel(deviceId: Long = lampa.id) = DeviceDetailViewModel(
        route = DeviceDetailRoute(deviceId),
        observeConnectedDevices = ObserveConnectedDevicesUseCase(ObserveDevicesUseCase(repository), connection),
        observeOutputs = ObserveOutputsUseCase(sessions),
        retryConnection = retry,
        setOutput = SetOutputUseCase(sessions),
        allOff = AllOffUseCase(sessions),
        updateAuthToken = UpdateAuthTokenUseCase(repository, retry),
        repairNode = RepairNodeUseCase(connection, sessions),
    )

    private fun content(state: ConnectionState) = DeviceDetailUiState.Content(lampa, state)

    @Test
    fun `shows the device with its connection state`() = runTest {
        viewModel().uiState.test {
            assertEquals(content(ConnectionState.Disconnected), awaitItem())
        }
    }

    @Test
    fun `follows the connection state while the app connects by itself`() = runTest {
        viewModel().uiState.test {
            awaitItem()

            connection.setState(lampa.address, ConnectionState.Waiting)
            assertEquals(content(ConnectionState.Waiting), awaitItem())

            connection.setState(lampa.address, ConnectionState.Connected)
            assertEquals(content(ConnectionState.Connected), awaitItem())
        }
    }

    @Test
    fun `shows not found when no device has the id`() = runTest {
        viewModel(deviceId = 99).uiState.test {
            assertEquals(DeviceDetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `shows not found when the device is removed`() = runTest {
        viewModel().uiState.test {
            awaitItem()

            repository.devices.value = emptyList()

            assertEquals(DeviceDetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `shows an error when storage cannot be read`() = runTest {
        repository.error.value = DeviceError.StorageUnavailable

        viewModel().uiState.test {
            assertEquals(DeviceDetailUiState.Error(DeviceError.StorageUnavailable), awaitItem())
        }
    }

    @Test
    fun `shows the error from a failed connection and offers a retry`() = runTest {
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.ConnectFailed(133)))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertEquals(ConnectionError.ConnectFailed(133), shown.error)
            assertTrue(shown.canRetry)
        }
    }

    @Test
    fun `offers no retry while the connection is fine`() = runTest {
        connection.setState(lampa.address, ConnectionState.Connected)

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertNull(shown.error)
            assertFalse(shown.canRetry)
        }
    }

    @Test
    fun `retry clears the error so the app connects again`() = runTest {
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.BluetoothOff))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onRetryClick()

            assertEquals(content(ConnectionState.Disconnected), awaitItem())
        }
        assertEquals(listOf(lampa.address), connection.disconnectCalls)
    }

    @Test
    fun `retry does nothing before the device is loaded`() = runTest {
        viewModel(deviceId = 99).onRetryClick()

        assertTrue(connection.disconnectCalls.isEmpty())
    }

    @Test
    fun `shows the outputs of an open session`() = runTest {
        sessions.setSession(lampa.address, open)

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertEquals(open.outputs.outputs, shown.outputs)
            assertTrue(shown.canControl)
            assertFalse(shown.showsLastKnownValues)
        }
    }

    @Test
    fun `shows the last known values when the session is closed`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Closed(open.outputs))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertTrue(shown.showsLastKnownValues)
            assertFalse(shown.canControl)
        }
    }

    @Test
    fun `sets an output through the session`() = runTest {
        sessions.setSession(lampa.address, open)
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onOutputChange(led, 1)
            skipItems(2) // Kommandot skickas och svaret kommer.
        }
        assertEquals(listOf(lampa.address to NodeCommand.SetDigital(led.id, on = true)), sessions.sent)
    }

    @Test
    fun `sends one command at a time`() = runTest {
        sessions.setSession(lampa.address, open)
        val reply = CompletableDeferred<Outcome<Unit, NodeFailure>>()
        sessions.sendResult = { reply.await() }
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onAllOffClick()
            val sending = awaitItem() as DeviceDetailUiState.Content
            assertTrue(sending.isSending)
            assertFalse(sending.canControl)

            viewModel.onOutputChange(led, 1)
            reply.complete(Outcome.Success(Unit))

            assertFalse((awaitItem() as DeviceDetailUiState.Content).isSending)
        }
        assertEquals(listOf(lampa.address to NodeCommand.AllOff), sessions.sent)
    }

    @Test
    fun `shows the error when the node rejects a command`() = runTest {
        sessions.setSession(lampa.address, open)
        sessions.sendResult = { Outcome.Failure(NodeFailure.Command(CommandError.InvalidOutput)) }
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onAllOffClick()
            skipItems(1)

            val shown = awaitItem() as DeviceDetailUiState.Content
            assertEquals(NodeFailure.Command(CommandError.InvalidOutput), shown.commandError)
        }
    }

    @Test
    fun `sends nothing when the session is not open`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Closed(open.outputs))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onOutputChange(led, 1)
            viewModel.onAllOffClick()
            expectNoEvents()
        }
        assertTrue(sessions.sent.isEmpty())
    }

    @Test
    fun `asks for a new token after a wrong token`() = runTest {
        val wrongToken = NodeSessionError.Node(NodeFailure.Command(CommandError.AuthFailed))
        sessions.setSession(lampa.address, NodeSession.Failed(wrongToken))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertTrue(shown.asksForToken)
            assertEquals(wrongToken, shown.sessionError)
            assertTrue(shown.canRetry)
        }
    }

    @Test
    fun `asks for a token when none is saved`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(NodeSessionError.TokenMissing))

        viewModel().uiState.test {
            assertTrue((awaitItem() as DeviceDetailUiState.Content).asksForToken)
        }
    }

    @Test
    fun `does not ask for a token for other errors`() = runTest {
        val tooOld = NodeSessionError.Node(NodeFailure.Connection(ConnectionError.PairingMissing))
        sessions.setSession(lampa.address, NodeSession.Failed(tooOld))

        viewModel().uiState.test {
            assertFalse((awaitItem() as DeviceDetailUiState.Content).asksForToken)
        }
    }

    @Test
    fun `saves a new token and connects again`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(NodeSessionError.TokenMissing))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenChange("nytt")
            awaitItem()
            viewModel.onTokenSubmit()

            val shown = expectMostRecentItem() as DeviceDetailUiState.Content
            assertEquals("", shown.token)
            assertEquals(NodeSession.Closed(), shown.session)
        }
        assertEquals(AuthToken.parse("nytt"), repository.tokens[lampa.address])
        assertEquals(listOf(lampa.address), sessions.resetCalls)
    }

    @Test
    fun `refuses an invalid token`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(NodeSessionError.TokenMissing))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenChange("")
            viewModel.onTokenSubmit()

            assertTrue((expectMostRecentItem() as DeviceDetailUiState.Content).tokenInvalid)
        }
        assertTrue(repository.tokens.isEmpty())
    }

    @Test
    fun `shows when the new token cannot be saved`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(NodeSessionError.TokenMissing))
        repository.tokenError = DeviceError.StorageUnavailable
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenChange("nytt")
            viewModel.onTokenSubmit()

            assertTrue((expectMostRecentItem() as DeviceDetailUiState.Content).tokenSaveFailed)
        }
        assertTrue(sessions.resetCalls.isEmpty())
    }

    @Test
    fun `offers to pair again instead of a retry when the phone is not paired`() = runTest {
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.PairingMissing))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertTrue(shown.canRepair)
            assertFalse(shown.canRetry)
        }
    }

    @Test
    fun `offers to pair again when the node has forgotten the phone`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(forgotten))

        viewModel().uiState.test {
            assertTrue((awaitItem() as DeviceDetailUiState.Content).canRepair)
        }
    }

    @Test
    fun `does not offer to pair again for other errors`() = runTest {
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.BluetoothOff))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertFalse(shown.canRepair)
            assertTrue(shown.canRetry)
        }
    }

    @Test
    fun `pairs the node again`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(forgotten))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onRepairClick()

            val shown = expectMostRecentItem() as DeviceDetailUiState.Content
            assertFalse(shown.isRepairing)
            assertNull(shown.repairError)
            assertFalse(shown.canRepair)
        }
        assertEquals(listOf(lampa.address), connection.pairCalls)
        assertEquals(listOf(lampa.address), sessions.resetCalls)
    }

    @Test
    fun `asks to remove the pairing in Android first`() = runTest {
        sessions.setSession(lampa.address, NodeSession.Failed(forgotten))
        connection.pairedInAndroid += lampa.address
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onRepairClick()

            val shown = expectMostRecentItem() as DeviceDetailUiState.Content
            assertEquals(RepairNodeError.StillPairedInAndroid, shown.repairError)
            assertTrue(shown.canRepair)
        }
        assertTrue(connection.pairCalls.isEmpty())
    }

    @Test
    fun `does not pair when nothing needs it`() = runTest {
        connection.setState(lampa.address, ConnectionState.Connected)
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onRepairClick()
            expectNoEvents()
        }
        assertTrue(connection.pairCalls.isEmpty())
    }

    @Test
    fun `keeps the pairing form while the app pairs`() {
        val pairing = DeviceDetailUiState.Content(lampa, ConnectionState.Connecting, isRepairing = true)

        assertTrue(pairing.showsRepairForm)
        assertFalse(pairing.canRepair)
    }

    private val forgotten = NodeSessionError.Node(NodeFailure.Connection(ConnectionError.PairingMissing))
}
