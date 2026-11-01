package io.github.ftatc.tinkertaphome.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Appens lokala databas.
 *
 * Varje schemaändring höjer [version] och får en migration i samma ändring.
 * `fallbackToDestructiveMigration()` används aldrig (se CLAUDE.md).
 */
@Database(
    entities = [DeviceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TinkerTapDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    companion object {
        const val NAME = "tinkertap.db"
    }
}
