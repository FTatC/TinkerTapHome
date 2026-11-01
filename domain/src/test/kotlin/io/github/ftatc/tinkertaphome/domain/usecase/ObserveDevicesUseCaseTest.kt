package io.github.ftatc.tinkertaphome.domain.usecase

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.Device
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
            assertEquals(emptyList<Device>(), awaitItem())
        }
    }

    @Test
    fun `sorts devices by name ignoring case`() = runTest {
        repository.devices.value = listOf(
            device(id = 1, name = "lampa"),
            device(id = 2, name = "Bryggare"),
            device(id = 3, name = "Relä"),
        )

        observeDevices().test {
            assertEquals(listOf("Bryggare", "lampa", "Relä"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `devices with equal names are ordered by id`() = runTest {
        repository.devices.value = listOf(
            device(id = 7, name = "Sensor"),
            device(id = 3, name = "sensor"),
        )

        observeDevices().test {
            assertEquals(listOf(3L, 7L), awaitItem().map { it.id })
        }
    }

    @Test
    fun `emits again when the repository changes`() = runTest {
        observeDevices().test {
            assertEquals(emptyList<Device>(), awaitItem())

            repository.devices.value = listOf(device(id = 1, name = "Lampa"))

            assertEquals(listOf("Lampa"), awaitItem().map { it.name })
        }
    }

    private fun device(id: Long, name: String) =
        Device(id = id, address = "AA:BB:CC:DD:EE:%02X".format(id), name = name)
}
