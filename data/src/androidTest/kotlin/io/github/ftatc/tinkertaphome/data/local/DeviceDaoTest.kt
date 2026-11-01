package io.github.ftatc.tinkertaphome.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {

    private lateinit var database: TinkerTapDatabase
    private lateinit var dao: DeviceDao

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
        val id = dao.insert(DeviceEntity(address = "AA:BB:CC:DD:EE:01", name = "Lampa"))

        dao.observeAll().test {
            assertEquals(
                listOf(DeviceEntity(id = id, address = "AA:BB:CC:DD:EE:01", name = "Lampa")),
                awaitItem(),
            )
        }
    }

    @Test
    fun insertingDuplicateAddressFails() = runTest {
        dao.insert(DeviceEntity(address = "AA:BB:CC:DD:EE:01", name = "Lampa"))

        val result = runCatching {
            dao.insert(DeviceEntity(address = "AA:BB:CC:DD:EE:01", name = "Kopia"))
        }

        assertTrue(result.exceptionOrNull() is SQLiteConstraintException)
    }
}
