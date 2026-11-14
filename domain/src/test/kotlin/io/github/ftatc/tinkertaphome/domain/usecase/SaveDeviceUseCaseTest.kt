package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
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
        assertTrue(repository.tokens.isEmpty())
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

    @Test
    fun `removes hidden characters before saving`() = runTest {
        // Regressionstest: kontrolltecken och tecken som vänder textriktningen sparades som de var.
        saveDevice(testAddress(1), "La\u202Empa\u0000 i\nköket")

        assertEquals(listOf(testAddress(1) to "Lampa i köket"), repository.saveCalls)
    }

    @Test
    fun `accepts a name of exactly the maximum length`() = runTest {
        val name = "a".repeat(DeviceNames.MAX_LENGTH)

        assertTrue(saveDevice(testAddress(1), name) is Outcome.Success)
    }

    @Test
    fun `rejects a name longer than the maximum without touching storage`() = runTest {
        val result = saveDevice(testAddress(1), "a".repeat(DeviceNames.MAX_LENGTH + 1))

        assertEquals(Outcome.Failure(SaveDeviceError.NameTooLong), result)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `a name of only hidden characters counts as missing`() = runTest {
        assertEquals(Outcome.Failure(SaveDeviceError.NameMissing), saveDevice(testAddress(1), "\u200B\u202E"))
    }

    @Test
    fun `checks and cleans a name without saving`() {
        assertEquals(Outcome.Success("Lampa"), saveDevice.checkName(" Lampa "))
        assertEquals(Outcome.Failure(SaveDeviceError.NameMissing), saveDevice.checkName(" "))
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun `saves the token with the device`() = runTest {
        saveDevice(testAddress(1), "Lampa", AuthToken.DEFAULT)

        assertEquals(AuthToken.DEFAULT, repository.tokens[testAddress(1)])
    }
}
