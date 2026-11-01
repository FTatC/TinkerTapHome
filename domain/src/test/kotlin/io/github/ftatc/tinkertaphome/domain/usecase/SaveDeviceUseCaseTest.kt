package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveDeviceUseCaseTest {

    private val repository = FakeDeviceRepository()
    private val saveDevice = SaveDeviceUseCase(repository)

    @Test
    fun `saves the device and returns it`() = runTest {
        val result = saveDevice(testAddress(1), "Lampa")

        assertEquals(Outcome.Success(testDevice(id = 1, name = "Lampa")), result)
        assertEquals(listOf(testDevice(id = 1, name = "Lampa")), repository.devices.value)
    }

    @Test
    fun `trims whitespace around the name`() = runTest {
        saveDevice(testAddress(1), "  Lampa i köket \n")

        assertEquals(listOf(testAddress(1) to "Lampa i köket"), repository.saveCalls)
    }

    @Test
    fun `rejects a blank name without touching storage`() = runTest {
        val result = saveDevice(testAddress(1), "   ")

        assertEquals(Outcome.Failure(SaveDeviceError.NameMissing), result)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `passes storage errors through`() = runTest {
        repository.saveError = SaveDeviceError.StorageUnavailable

        assertEquals(Outcome.Failure(SaveDeviceError.StorageUnavailable), saveDevice(testAddress(1), "Lampa"))
    }

    @Test
    fun `reports a device that is already saved`() = runTest {
        repository.devices.value = listOf(testDevice(id = 1))

        assertEquals(Outcome.Failure(SaveDeviceError.AlreadySaved), saveDevice(testAddress(1), "Kopia"))
    }
}
