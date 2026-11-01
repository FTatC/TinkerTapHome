package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveDevicesUseCaseTest {

    private val repository = FakeDeviceRepository()
    private val observeDevices = ObserveDevicesUseCase(repository)

    @Test
    fun `emits empty list when no devices are saved`() = runTest {
        observeDevices().test {
            assertEquals(Outcome.Success(emptyList<Device>()), awaitItem())
        }
    }

    @Test
    fun `sorts devices by name ignoring case`() = runTest {
        repository.devices.value = listOf(
            testDevice(id = 1, name = "lampa"),
            testDevice(id = 2, name = "Bryggare"),
            testDevice(id = 3, name = "Relä"),
        )

        observeDevices().test {
            assertEquals(listOf("Bryggare", "lampa", "Relä"), awaitDevices().map { it.name })
        }
    }

    @Test
    fun `devices with equal names are ordered by id`() = runTest {
        repository.devices.value = listOf(
            testDevice(id = 7, name = "Sensor"),
            testDevice(id = 3, name = "sensor"),
        )

        observeDevices().test {
            assertEquals(listOf(3L, 7L), awaitDevices().map { it.id })
        }
    }

    @Test
    fun `emits again when the repository changes`() = runTest {
        observeDevices().test {
            assertEquals(emptyList<Device>(), awaitDevices())

            repository.devices.value = listOf(testDevice(id = 1, name = "Lampa"))

            assertEquals(listOf("Lampa"), awaitDevices().map { it.name })
        }
    }

    @Test
    fun `passes storage errors through`() = runTest {
        repository.error.value = DeviceError.StorageUnavailable

        observeDevices().test {
            assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), awaitItem())
        }
    }

    private suspend fun ReceiveTurbine<Outcome<List<Device>, DeviceError>>.awaitDevices(): List<Device> {
        val outcome = awaitItem()
        check(outcome is Outcome.Success) { "Förväntade Success men fick $outcome" }
        return outcome.value
    }
}
