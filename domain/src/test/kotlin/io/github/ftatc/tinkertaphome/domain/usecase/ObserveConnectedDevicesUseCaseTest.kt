package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveConnectedDevicesUseCaseTest {

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val rela = testDevice(id = 2, name = "Relä")

    private val repository = FakeDeviceRepository(listOf(rela, lampa))
    private val connection = FakeDeviceConnection()
    private val useCase = ObserveConnectedDevicesUseCase(ObserveDevicesUseCase(repository), connection)

    @Test
    fun `pairs each device with its connection state in name order`() = runTest {
        connection.setState(lampa.address, ConnectionState.Connected)

        useCase().test {
            assertEquals(
                Outcome.Success(
                    listOf(
                        ConnectedDevice(lampa, ConnectionState.Connected),
                        ConnectedDevice(rela, ConnectionState.Disconnected),
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `emits a new list when a connection state changes`() = runTest {
        useCase().test {
            awaitItem()

            connection.setState(rela.address, ConnectionState.Failed(ConnectionError.ConnectionLost))

            val devices = (awaitItem() as Outcome.Success).value
            assertEquals(ConnectionState.Failed(ConnectionError.ConnectionLost), devices.single { it.device == rela }.connection)
        }
    }

    @Test
    fun `emits a new list when a device is added`() = runTest {
        repository.devices.value = emptyList()

        useCase().test {
            assertEquals(Outcome.Success(emptyList<ConnectedDevice>()), awaitItem())

            repository.devices.value = listOf(lampa)

            assertEquals(Outcome.Success(listOf(ConnectedDevice(lampa, ConnectionState.Disconnected))), awaitItem())
        }
    }

    @Test
    fun `passes on storage errors`() = runTest {
        repository.error.value = DeviceError.StorageUnavailable

        useCase().test {
            assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), awaitItem())
        }
    }

    @Test
    fun `device follows one device by id`() = runTest {
        useCase.device(lampa.id).test {
            assertEquals(Outcome.Success(ConnectedDevice(lampa, ConnectionState.Disconnected)), awaitItem())

            connection.setState(lampa.address, ConnectionState.Connecting)

            assertEquals(Outcome.Success(ConnectedDevice(lampa, ConnectionState.Connecting)), awaitItem())
        }
    }

    @Test
    fun `device is null when no device has the id`() = runTest {
        useCase.device(id = 99).test {
            assertEquals(Outcome.Success(null), awaitItem())
        }
    }
}
