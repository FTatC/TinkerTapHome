package io.github.ftatc.tinkertaphome.ui.device

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.RetryConnectionUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
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

    private fun viewModel(deviceId: Long = lampa.id) = DeviceDetailViewModel(
        route = DeviceDetailRoute(deviceId),
        observeConnectedDevices = ObserveConnectedDevicesUseCase(ObserveDevicesUseCase(repository), connection),
        retryConnection = RetryConnectionUseCase(connection),
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
        connection.setState(lampa.address, ConnectionState.Failed(ConnectionError.PairingMissing))

        viewModel().uiState.test {
            val shown = awaitItem() as DeviceDetailUiState.Content
            assertEquals(ConnectionError.PairingMissing, shown.error)
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
}
