package io.github.ftatc.tinkertaphome.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushButtonDaoTest {

    private lateinit var database: TinkerTapDatabase
    private lateinit var devices: DeviceDao
    private lateinit var dao: PushButtonDao
    private val address = testAddress(1).value

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TinkerTapDatabase::class.java,
        ).build()
        devices = database.deviceDao()
        dao = database.pushButtonDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savedPushButtonIsObservedForItsNodeOnly() = runTest {
        devices.insert(DeviceEntity(address = address, name = "Lampa"))
        devices.insert(DeviceEntity(address = testAddress(2).value, name = "Relä"))
        dao.insert(PushButtonEntity(address, "btn"))

        assertEquals(listOf("btn"), dao.observeOutputNames(address).first())
        assertEquals(emptyList<String>(), dao.observeOutputNames(testAddress(2).value).first())
    }

    @Test
    fun savingTheSamePushButtonTwiceKeepsOneRow() = runTest {
        devices.insert(DeviceEntity(address = address, name = "Lampa"))

        dao.insert(PushButtonEntity(address, "btn"))
        dao.insert(PushButtonEntity(address, "btn"))

        assertEquals(listOf("btn"), dao.observeOutputNames(address).first())
    }

    @Test
    fun deletedPushButtonIsNoLongerObserved() = runTest {
        devices.insert(DeviceEntity(address = address, name = "Lampa"))
        dao.insert(PushButtonEntity(address, "btn"))

        dao.observeOutputNames(address).test {
            assertEquals(listOf("btn"), awaitItem())

            dao.delete(address, "btn")

            assertEquals(emptyList<String>(), awaitItem())
        }
    }

    @Test
    fun pushButtonForANodeThatIsNotSavedFails() = runTest {
        val result = runCatching { dao.insert(PushButtonEntity(address, "btn")) }

        assertTrue(result.exceptionOrNull() is SQLiteConstraintException)
    }

    @Test
    fun pushButtonsAreRemovedWithTheirNode() = runTest {
        devices.insert(DeviceEntity(address = address, name = "Lampa"))
        dao.insert(PushButtonEntity(address, "btn"))

        database.openHelper.writableDatabase.execSQL("DELETE FROM devices WHERE address = '$address'")

        assertEquals(emptyList<String>(), dao.observeOutputNames(address).first())
    }
}
