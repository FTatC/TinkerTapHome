package io.github.ftatc.tinkertaphome.ui.devices

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
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

    private val lampa = Device(id = 1, address = "AA:BB:CC:DD:EE:01", name = "Lampa")
    private val rela = Device(id = 2, address = "AA:BB:CC:DD:EE:02", name = "Relä")

    @Test
    fun `starts in loading until the first list arrives`() = runTest {
        val repository = PendingDeviceRepository()
        val viewModel = DeviceListViewModel(ObserveDevicesUseCase(repository))

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Loading, awaitItem())

            repository.emit(emptyList())

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
    fun `shows devices sorted by name`() = runTest {
        val viewModel = viewModelWith(listOf(rela, lampa))

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Content(listOf(lampa, rela)), awaitItem())
        }
    }

    @Test
    fun `updates when devices change`() = runTest {
        val repository = FakeDeviceRepository()
        val viewModel = DeviceListViewModel(ObserveDevicesUseCase(repository))

        viewModel.uiState.test {
            assertEquals(DeviceListUiState.Empty, awaitItem())

            repository.devices.value = listOf(lampa)

            assertEquals(DeviceListUiState.Content(listOf(lampa)), awaitItem())
        }
    }

    private fun viewModelWith(devices: List<Device>) =
        DeviceListViewModel(ObserveDevicesUseCase(FakeDeviceRepository(devices)))

    /** Repository som inte skickar något förrän testet anropar [emit], så att Loading syns. */
    private class PendingDeviceRepository : DeviceRepository {
        private val flow = MutableSharedFlow<List<Device>>()
        override fun observeDevices(): Flow<List<Device>> = flow
        suspend fun emit(devices: List<Device>) = flow.emit(devices)
    }
}
