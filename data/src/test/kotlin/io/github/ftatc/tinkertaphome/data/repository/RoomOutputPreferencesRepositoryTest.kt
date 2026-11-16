package io.github.ftatc.tinkertaphome.data.repository

import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.data.local.PushButtonDao
import io.github.ftatc.tinkertaphome.data.local.PushButtonEntity
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOutputPreferencesRepositoryTest {

    private val address = testAddress(1)
    private val dao = FakePushButtonDao()
    private val repository = RoomOutputPreferencesRepository(dao)

    @Test
    fun `streams the push buttons of the node`() = runTest {
        dao.rows.value = setOf(PushButtonEntity(address.value, "btn"), PushButtonEntity(testAddress(2).value, "other"))

        repository.observePushButtons(address).test {
            assertEquals(Outcome.Success(setOf("btn")), awaitItem())
        }
    }

    @Test
    fun `saves and removes a push button`() = runTest {
        repository.setPushButton(address, "btn", pushButton = true)
        assertEquals(setOf(PushButtonEntity(address.value, "btn")), dao.rows.value)

        repository.setPushButton(address, "btn", pushButton = false)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `reports a storage error when reading fails`() = runTest {
        dao.readFailure = IllegalStateException("databasen är skadad")

        repository.observePushButtons(address).test {
            assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `reports a storage error when writing fails`() = runTest {
        dao.writeFailure = IllegalStateException("Disken är full")

        assertEquals(
            Outcome.Failure(DeviceError.StorageUnavailable),
            repository.setPushButton(address, "btn", pushButton = true),
        )
    }

    @Test
    fun `does not swallow cancellation`() = runTest {
        dao.writeFailure = CancellationException("avbruten")

        val thrown = runCatching { repository.setPushButton(address, "btn", pushButton = true) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    private class FakePushButtonDao : PushButtonDao {
        val rows = MutableStateFlow<Set<PushButtonEntity>>(emptySet())
        var readFailure: Throwable? = null
        var writeFailure: Throwable? = null

        override fun observeOutputNames(address: String): Flow<List<String>> =
            readFailure?.let { error -> flow { throw error } }
                ?: rows.map { all -> all.filter { it.deviceAddress == address }.map { it.outputName } }

        override suspend fun insert(pushButton: PushButtonEntity) {
            writeFailure?.let { throw it }
            rows.update { it + pushButton }
        }

        override suspend fun delete(address: String, outputName: String) {
            writeFailure?.let { throw it }
            rows.update { all -> all - PushButtonEntity(address, outputName) }
        }
    }
}
