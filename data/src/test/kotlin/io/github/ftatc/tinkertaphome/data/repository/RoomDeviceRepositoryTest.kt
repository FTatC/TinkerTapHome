package io.github.ftatc.tinkertaphome.data.repository

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.DeviceEntity
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDeviceRepositoryTest {

    private val dao = FakeDeviceDao()
    private val repository = RoomDeviceRepository(dao)

    @Test
    fun `maps entities to domain devices`() = runTest {
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa"))

        repository.observeDevices().test {
            assertEquals(listOf(testDevice(id = 1, name = "Lampa")), awaitDevices())
        }
    }

    @Test
    fun `emits again when the dao changes`() = runTest {
        repository.observeDevices().test {
            assertEquals(emptyList<Device>(), awaitDevices())

            dao.entities.value = listOf(DeviceEntity(id = 2, address = testAddress(2).value, name = "Relä"))

            assertEquals(listOf("Relä"), awaitDevices().map { it.name })
        }
    }

    @Test
    fun `normalizes addresses stored in lower case`() = runTest {
        // Regressionstest: en adress med gemener ska ge samma domänadress som med versaler.
        dao.entities.value = listOf(DeviceEntity(id = 1, address = "aa:bb:cc:dd:ee:01", name = "Lampa"))

        repository.observeDevices().test {
            assertEquals(listOf(testAddress(1)), awaitDevices().map { it.address })
        }
    }

    @Test
    fun `skips rows with an invalid address instead of failing the whole list`() = runTest {
        dao.entities.value = listOf(
            DeviceEntity(id = 1, address = "inte en adress", name = "Trasig"),
            DeviceEntity(id = 2, address = testAddress(2).value, name = "Relä"),
        )

        repository.observeDevices().test {
            assertEquals(listOf(testDevice(id = 2, name = "Relä")), awaitDevices())
        }
    }

    @Test
    fun `turns a storage exception into a failure instead of throwing`() = runTest {
        dao.failure = IllegalStateException("Databasen är skadad")

        repository.observeDevices().test {
            assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `does not swallow cancellation`() = runTest {
        dao.failure = CancellationException("avbruten")

        val result = runCatching { repository.observeDevices().toList() }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    private suspend fun ReceiveTurbine<Outcome<List<Device>, DeviceError>>.awaitDevices(): List<Device> {
        val outcome = awaitItem()
        check(outcome is Outcome.Success) { "Förväntade Success men fick $outcome" }
        return outcome.value
    }

    private class FakeDeviceDao : DeviceDao {
        val entities = MutableStateFlow<List<DeviceEntity>>(emptyList())

        /** Sätts för att låta [observeAll] kasta, som Room gör när databasen inte går att läsa. */
        var failure: Throwable? = null

        override fun observeAll(): Flow<List<DeviceEntity>> =
            failure?.let { error -> flow { throw error } } ?: entities

        override suspend fun insert(device: DeviceEntity): Long =
            throw NotImplementedError("RoomDeviceRepository skriver inte till DAO:n än")
    }
}
