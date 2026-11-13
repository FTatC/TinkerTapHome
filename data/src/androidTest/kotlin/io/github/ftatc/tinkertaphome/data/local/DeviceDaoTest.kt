package io.github.ftatc.tinkertaphome.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {

    private lateinit var database: TinkerTapDatabase
    private lateinit var dao: DeviceDao
    private val address = testAddress(1).value

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinkerTapDatabase::class.java,
        ).build()
        dao = database.deviceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertedDeviceIsObserved() = runTest {
        val id = dao.insert(DeviceEntity(address = address, name = "Lampa"))

        dao.observeAll().test {
            assertEquals(
                listOf(DeviceEntity(id = id, address = address, name = "Lampa")),
                awaitItem(),
            )
        }
    }

    @Test
    fun insertingDuplicateAddressFails() = runTest {
        dao.insert(DeviceEntity(address = address, name = "Lampa"))

        val result = runCatching {
            dao.insert(DeviceEntity(address = address, name = "Kopia"))
        }

        assertTrue(result.exceptionOrNull() is SQLiteConstraintException)
    }

    @Test
    fun findsADeviceByAddressWithItsToken() = runTest {
        val id = dao.insert(DeviceEntity(address = address, name = "Lampa", authToken = "tinker"))

        assertEquals(DeviceEntity(id = id, address = address, name = "Lampa", authToken = "tinker"), dao.findByAddress(address))
        assertNull(dao.findByAddress(testAddress(2).value))
    }

    @Test
    fun updatesTheTokenOfOneDevice() = runTest {
        dao.insert(DeviceEntity(address = address, name = "Lampa"))

        assertEquals(1, dao.updateAuthToken(address, "nytt"))
        assertEquals(0, dao.updateAuthToken(testAddress(2).value, "nytt"))

        assertEquals("nytt", dao.findByAddress(address)?.authToken)
    }
}
