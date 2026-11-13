package io.github.ftatc.tinkertaphome.ui.devices

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val rela = testDevice(id = 2, name = "Relä")
    private val connection = FakeDeviceConnection()

    private fun viewModel(repository: DeviceRepository) =
        DeviceListViewModel(ObserveConnectedDevicesUseCase(ObserveDevicesUseCase(repository), connection))

    private fun disconnected(device: Device) = ConnectedDevice(device, ConnectionState.Disconnected)

    @Test
    fun `starts in loading until the first list arrives`() = runTest {
        val repository = PendingDeviceRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Loading, awaitItem())

            repository.emit(Outcome.Success(emptyList()))

            assertEquals(DeviceListUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `shows empty state when no devices are saved`() = runTest {
        val viewModel = viewModelWith(emptyList())

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `shows saved devices with their connection state in the order the use case delivers them`() = runTest {
        // Sorteringen testas i ObserveDevicesUseCaseTest. Här skickas listan in redan sorterad.
        connection.setState(rela.address, ConnectionState.Connected)
        val viewModel = viewModelWith(listOf(lampa, rela))

        viewModel.uiState.test {
            assertEquals(
                DeviceListUiState.Content(listOf(disconnected(lampa), ConnectedDevice(rela, ConnectionState.Connected))),
                awaitItem(),
            )
        }
    }

    @Test
    fun `updates when a connection state changes`() = runTest {
        val viewModel = viewModelWith(listOf(lampa))

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Content(listOf(disconnected(lampa))), awaitItem())

            connection.setState(lampa.address, ConnectionState.Connecting)

            assertEquals(DeviceListUiState.Content(listOf(ConnectedDevice(lampa, ConnectionState.Connecting))), awaitItem())
        }
    }

    @Test
    fun `updates when devices change`() = runTest {
        val repository = FakeDeviceRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Empty, awaitItem())

            repository.devices.value = listOf(lampa)

            assertEquals(DeviceListUiState.Content(listOf(disconnected(lampa))), awaitItem())
        }
    }

    @Test
    fun `shows error state when storage cannot be read`() = runTest {
        val repository = FakeDeviceRepository().apply { error.value = DeviceError.StorageUnavailable }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Error(DeviceError.StorageUnavailable), awaitItem())
        }
    }

    private fun viewModelWith(devices: List<Device>) = viewModel(FakeDeviceRepository(devices))

    /** Repository som inte skickar något förrän testet anropar [emit], så att Loading syns. */
    private class PendingDeviceRepository : DeviceRepository {
        private val flow = MutableSharedFlow<Outcome<List<Device>, DeviceError>>()
        override fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>> = flow
        suspend fun emit(outcome: Outcome<List<Device>, DeviceError>) = flow.emit(outcome)
        override suspend fun save(address: BleAddress, name: String, token: AuthToken?): Outcome<Device, SaveDeviceError> =
            throw NotImplementedError("Enhetslistan sparar inga enheter")
        override suspend fun authToken(address: BleAddress): Outcome<AuthToken?, DeviceError> =
            throw NotImplementedError("Enhetslistan läser inga token")
        override suspend fun updateAuthToken(address: BleAddress, token: AuthToken?): Outcome<Unit, DeviceError> =
            throw NotImplementedError("Enhetslistan ändrar inga token")
    }
}
