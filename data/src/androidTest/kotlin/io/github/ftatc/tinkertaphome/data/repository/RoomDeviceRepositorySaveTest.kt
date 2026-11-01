package io.github.ftatc.tinkertaphome.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ftatc.tinkertaphome.data.local.TinkerTapDatabase
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Testar [RoomDeviceRepository.save] mot en riktig Room-databas, eftersom felet kommer från SQLite. */
@RunWith(AndroidJUnit4::class)
class RoomDeviceRepositorySaveTest {

    private lateinit var database: TinkerTapDatabase
    private lateinit var repository: RoomDeviceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinkerTapDatabase::class.java,
        ).build()
        repository = RoomDeviceRepository(database.deviceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savedDeviceIsObserved() = runTest {
        val saved = repository.save(testAddress(1), "Lampa")

        assertTrue(saved is Outcome.Success)
        assertEquals(Outcome.Success(listOf(testDevice(id = 1, name = "Lampa"))), repository.observeDevices().first())
    }

    @Test
    fun savingTheSameAddressAgainIsReportedAsAlreadySaved() = runTest {
        repository.save(testAddress(1), "Lampa")

        assertEquals(Outcome.Failure(SaveDeviceError.AlreadySaved), repository.save(testAddress(1), "Kopia"))
    }

    @Test
    fun anAddressWrittenInLowerCaseIsTheSameDevice() = runTest {
        repository.save(testAddress(1), "Lampa")
        val lowerCase = checkNotNull(BleAddress.parse(testAddress(1).value.lowercase()))

        assertEquals(Outcome.Failure(SaveDeviceError.AlreadySaved), repository.save(lowerCase, "Kopia"))
    }
}
