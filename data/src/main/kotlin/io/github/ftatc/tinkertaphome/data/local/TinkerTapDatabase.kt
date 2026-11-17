package io.github.ftatc.tinkertaphome.data.local

import android.content.Context
import androidx.room.AutoMigration
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
    entities = [DeviceEntity::class, PushButtonEntity::class],
    version = TinkerTapDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // Version 2: kolumnen auth_token i devices, som kan vara tom (fas 6, steg 6.4).
        AutoMigration(from = 1, to = 2),
        // Version 3: tabellen push_buttons för utgångar som visas som tryckknappar (fas 7, steg 7.5).
        AutoMigration(from = 2, to = 3),
    ],
)
internal abstract class TinkerTapDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    abstract fun pushButtonDao(): PushButtonDao

    companion object {
        const val NAME = "tinkertap.db"
        const val VERSION = 3

        /** Bygger databasen med appens konfiguration. Används av Hilt och av migreringstesterna. */
        fun build(context: Context, name: String = NAME): TinkerTapDatabase =
            Room.databaseBuilder(context, TinkerTapDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
