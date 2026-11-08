package io.github.ftatc.tinkertaphome.ui.device

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun viewModel(deviceId: Long = lampa.id) = DeviceDetailViewModel(
        route = DeviceDetailRoute(deviceId),
        observeConnectedDevices = ObserveConnectedDevicesUseCase(ObserveDevicesUseCase(repository), connection),
        connection = connection,
    )

    private fun content(state: ConnectionState, connectError: ConnectionError? = null) =
        DeviceDetailUiState.Content(lampa, state, connectError)

    @Test
    fun `shows the device with its connection state`() = runTest {
        viewModel().uiState.test {
            assertEquals(content(ConnectionState.Disconnected), awaitItem())
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
    fun `connect connects the device and follows its state`() = runTest {
        connection.holdConnect = true
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onConnectClick()
            assertEquals(content(ConnectionState.Connecting), awaitItem())

            connection.finishConnect(Outcome.Success(Unit))
            assertEquals(content(ConnectionState.Connected), awaitItem())
        }
        assertEquals(listOf(lampa.address), connection.connectCalls)
    }

    @Test
    fun `shows the error when the connection could not be started`() = runTest {
        connection.connectError = ConnectionError.BluetoothOff
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onConnectClick()

            val shown = awaitItem() as DeviceDetailUiState.Content
            assertEquals(ConnectionState.Disconnected, shown.connection)
            assertEquals(ConnectionError.BluetoothOff, shown.error)
        }
    }

    @Test
    fun `shows the error from a failed connection`() = runTest {
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.BondFailed))

        viewModel().uiState.test {
            assertEquals(ConnectionError.BondFailed, (awaitItem() as DeviceDetailUiState.Content).error)
        }
    }

    @Test
    fun `a new attempt clears the previous error`() = runTest {
        connection.connectError = ConnectionError.BluetoothOff
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onConnectClick()
            awaitItem()

            connection.connectError = null
            viewModel.onConnectClick()

            var shown = awaitItem() as DeviceDetailUiState.Content
            assertNull(shown.error)
            while (shown.connection != ConnectionState.Connected) shown = awaitItem() as DeviceDetailUiState.Content
            assertNull(shown.error)
        }
    }

    @Test
    fun `disconnect disconnects the device`() = runTest {
        connection.setState(lampa.address, ConnectionState.Connected)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(content(ConnectionState.Connected), awaitItem())

            viewModel.onDisconnectClick()

            assertEquals(content(ConnectionState.Disconnected), awaitItem())
        }
        assertEquals(listOf(lampa.address), connection.disconnectCalls)
    }

    @Test
    fun `buttons do nothing before the device is loaded`() = runTest {
        val viewModel = viewModel(deviceId = 99)

        viewModel.onConnectClick()
        viewModel.onDisconnectClick()

        assertTrue(connection.connectCalls.isEmpty())
        assertTrue(connection.disconnectCalls.isEmpty())
    }
}
