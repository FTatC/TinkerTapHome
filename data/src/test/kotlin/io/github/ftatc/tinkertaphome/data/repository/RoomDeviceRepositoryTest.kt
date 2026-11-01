package io.github.ftatc.tinkertaphome.data.repository

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.DeviceEntity
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
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
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa"))

        repository.observeDevices().test {
            assertEquals(listOf(testDevice(id = 1, name = "Lampa")), awaitItem())
        }
    }

    @Test
    fun `emits again when the dao changes`() = runTest {
        repository.observeDevices().test {
            assertEquals(emptyList<Device>(), awaitItem())

            dao.entities.value = listOf(DeviceEntity(id = 2, address = testAddress(2).value, name = "Relä"))

            assertEquals(listOf("Relä"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `normalizes addresses stored in lower case`() = runTest {
        // Regressionstest: en adress med gemener ska ge samma domänadress som med versaler.
        dao.entities.value = listOf(DeviceEntity(id = 1, address = "aa:bb:cc:dd:ee:01", name = "Lampa"))

        repository.observeDevices().test {
            assertEquals(listOf(testAddress(1)), awaitItem().map { it.address })
        }
    }

    @Test
    fun `skips rows with an invalid address instead of failing the whole list`() = runTest {
        dao.entities.value = listOf(
            DeviceEntity(id = 1, address = "inte en adress", name = "Trasig"),
            DeviceEntity(id = 2, address = testAddress(2).value, name = "Relä"),
        )

        repository.observeDevices().test {
            assertEquals(listOf(testDevice(id = 2, name = "Relä")), awaitItem())
        }
    }

    private class FakeDeviceDao : DeviceDao {
        val entities = MutableStateFlow<List<DeviceEntity>>(emptyList())

        override fun observeAll(): Flow<List<DeviceEntity>> = entities

        override suspend fun insert(device: DeviceEntity): Long =
            throw NotImplementedError("RoomDeviceRepository skriver inte till DAO:n än")
    }
}
