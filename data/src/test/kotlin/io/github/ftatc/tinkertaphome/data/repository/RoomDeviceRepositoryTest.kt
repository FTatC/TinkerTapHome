package io.github.ftatc.tinkertaphome.data.repository

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.DeviceEntity
import io.github.ftatc.tinkertaphome.domain.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomDeviceRepositoryTest {

    private val dao = FakeDeviceDao()
    private val repository = RoomDeviceRepository(dao)

    @Test
    fun `maps entities to domain devices`() = runTest {
        dao.entities.value = listOf(
            DeviceEntity(id = 1, address = "AA:BB:CC:DD:EE:01", name = "Lampa"),
        )

        repository.observeDevices().test {
            assertEquals(
                listOf(Device(id = 1, address = "AA:BB:CC:DD:EE:01", name = "Lampa")),
                awaitItem(),
            )
        }
    }

    @Test
    fun `emits again when the dao changes`() = runTest {
        repository.observeDevices().test {
            assertEquals(emptyList<Device>(), awaitItem())

            dao.entities.value = listOf(DeviceEntity(id = 2, address = "AA:BB:CC:DD:EE:02", name = "Relä"))

            assertEquals(listOf("Relä"), awaitItem().map { it.name })
        }
    }

    private class FakeDeviceDao : DeviceDao {
        val entities = MutableStateFlow<List<DeviceEntity>>(emptyList())

        override fun observeAll(): Flow<List<DeviceEntity>> = entities

        override suspend fun insert(device: DeviceEntity): Long {
            val id = device.id.takeIf { it != 0L } ?: (entities.value.size + 1L)
            entities.value += device.copy(id = id)
            return id
        }
    }
}
