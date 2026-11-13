package io.github.ftatc.tinkertaphome.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifierar att en databas från varje tidigare schemaversion kan uppgraderas till nuvarande version
 * med migreringarna i [ALL_MIGRATIONS], och att resultatet stämmer med det exporterade schemat.
 *
 * Schemafilerna i `data/schemas/` används som utgångspunkt, så de måste vara incheckade.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TinkerTapDatabase::class.java,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun allMigrationsProduceTheExportedLatestSchema() {
        helper.createDatabase(TEST_DB, FIRST_VERSION).close()

        helper.runMigrationsAndValidate(TEST_DB, TinkerTapDatabase.VERSION, true, *ALL_MIGRATIONS)
            .close()
    }

    @Test
    fun appCanOpenADatabaseCreatedWithTheFirstSchemaVersion() {
        // Fångar en höjd version utan registrerad migration: Room kastar då när databasen öppnas.
        helper.createDatabase(TEST_DB, FIRST_VERSION).close()

        val database = TinkerTapDatabase.build(context, TEST_DB)
        try {
            assertEquals(TinkerTapDatabase.VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun dataSurvivesMigrationFromTheFirstSchemaVersion() {
        helper.createDatabase(TEST_DB, FIRST_VERSION).use { db ->
            db.execSQL("INSERT INTO devices (id, address, name) VALUES (1, 'AA:BB:CC:DD:EE:01', 'Lampa')")
        }

        val database = TinkerTapDatabase.build(context, TEST_DB)
        try {
            database.openHelper.readableDatabase
                .query("SELECT address, name FROM devices WHERE id = 1").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("AA:BB:CC:DD:EE:01", cursor.getString(0))
                    assertEquals("Lampa", cursor.getString(1))
                }
        } finally {
            database.close()
        }
    }

    @Test
    fun existingDevicesHaveNoTokenAfterMigratingToVersion2() {
        helper.createDatabase(TEST_DB, FIRST_VERSION).use { db ->
            db.execSQL("INSERT INTO devices (id, address, name) VALUES (1, 'AA:BB:CC:DD:EE:01', 'Lampa')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT name, auth_token FROM devices WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Lampa", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Den första schemaversionen som har funnits. Ändras aldrig. */
        const val FIRST_VERSION = 1
    }
}
