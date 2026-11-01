package io.github.ftatc.tinkertaphome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Appens lokala databas.
 *
 * Varje schemaändring höjer [VERSION] och får en migration i [ALL_MIGRATIONS] i samma ändring.
 * `fallbackToDestructiveMigration()` används aldrig (se CLAUDE.md).
 */
@Database(
    entities = [DeviceEntity::class],
    version = TinkerTapDatabase.VERSION,
    exportSchema = true,
)
internal abstract class TinkerTapDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    companion object {
        const val NAME = "tinkertap.db"
        const val VERSION = 1

        /** Bygger databasen med appens konfiguration. Används av Hilt och av migreringstesterna. */
        fun build(context: Context, name: String = NAME): TinkerTapDatabase =
            Room.databaseBuilder(context, TinkerTapDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
