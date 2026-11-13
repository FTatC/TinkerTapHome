package io.github.ftatc.tinkertaphome.data.repository

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.DeviceEntity
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `save inserts the device and returns it with the new id`() = runTest {
        val result = repository.save(testAddress(1), "Lampa", token = null)

        assertEquals(Outcome.Success(testDevice(id = 1, name = "Lampa")), result)
        assertEquals(listOf(DeviceEntity(address = testAddress(1).value, name = "Lampa")), dao.inserted)
    }

    @Test
    fun `save turns a storage exception into a failure`() = runTest {
        dao.insertFailure = IllegalStateException("Disken är full")

        assertEquals(Outcome.Failure(SaveDeviceError.StorageUnavailable), repository.save(testAddress(1), "Lampa", token = null))
    }

    @Test
    fun `save does not swallow cancellation`() = runTest {
        dao.insertFailure = CancellationException("avbruten")

        val result = runCatching { repository.save(testAddress(1), "Lampa", token = null) }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `save stores the token with the device`() = runTest {
        repository.save(testAddress(1), "Lampa", AuthToken.DEFAULT)

        assertEquals("tinker", dao.inserted.single().authToken)
    }

    @Test
    fun `reads the saved token`() = runTest {
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa", authToken = "hemligt"))

        assertEquals(Outcome.Success(AuthToken.parse("hemligt")), repository.authToken(testAddress(1)))
    }

    @Test
    fun `has no token for a device without one or a device that does not exist`() = runTest {
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa"))

        assertEquals(Outcome.Success(null), repository.authToken(testAddress(1)))
        assertEquals(Outcome.Success(null), repository.authToken(testAddress(2)))
    }

    @Test
    fun `treats a stored token that is no longer valid as no token`() = runTest {
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa", authToken = "x".repeat(40)))

        assertEquals(Outcome.Success(null), repository.authToken(testAddress(1)))
    }

    @Test
    fun `updates and removes the token`() = runTest {
        dao.entities.value = listOf(DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa"))
        val token = checkNotNull(AuthToken.parse("nytt"))

        assertEquals(Outcome.Success(Unit), repository.updateAuthToken(testAddress(1), token))
        assertEquals(Outcome.Success(token), repository.authToken(testAddress(1)))

        repository.updateAuthToken(testAddress(1), null)
        assertEquals(Outcome.Success(null), repository.authToken(testAddress(1)))
    }

    @Test
    fun `turns a storage exception when reading or updating the token into a failure`() = runTest {
        dao.queryFailure = IllegalStateException("Databasen är skadad")

        assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), repository.authToken(testAddress(1)))
        assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), repository.updateAuthToken(testAddress(1), AuthToken.DEFAULT))
    }

    @Test
    fun `token access does not swallow cancellation`() = runTest {
        dao.queryFailure = CancellationException("avbruten")

        val result = runCatching { repository.authToken(testAddress(1)) }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `never shows the token when an entity is printed`() {
        val entity = DeviceEntity(id = 1, address = testAddress(1).value, name = "Lampa", authToken = "hemligt")

        assertFalse(entity.toString().contains("hemligt"))
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

        /** Sätts för att låta [insert] kasta. */
        var insertFailure: Throwable? = null

        val inserted = mutableListOf<DeviceEntity>()

        override suspend fun insert(device: DeviceEntity): Long {
            insertFailure?.let { throw it }
            inserted += device
            return inserted.size.toLong()
        }

        /** Sätts för att låta [findByAddress] och [updateAuthToken] kasta. */
        var queryFailure: Throwable? = null

        override suspend fun findByAddress(address: String): DeviceEntity? {
            queryFailure?.let { throw it }
            return entities.value.find { it.address == address }
        }

        override suspend fun updateAuthToken(address: String, token: String?): Int {
            queryFailure?.let { throw it }
            val before = entities.value
            entities.value = before.map { if (it.address == address) it.copy(authToken = token) else it }
            return before.count { it.address == address }
        }
    }
}
