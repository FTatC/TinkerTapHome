package io.github.ftatc.tinkertaphome.data.local

import androidx.room.migration.Migration

/**
 * Alla manuella migreringar, i versionsordning. Varje gång [TinkerTapDatabase.VERSION] höjs ska en
 * migration läggas till här i samma ändring, om den inte är en `AutoMigration` på `@Database`.
 *
 * Listan används både av appen och av `MigrationTest`, så en migration som glöms bort här
 * gör att testet fallerar i stället för att appen kraschar hos användare med en äldre databas.
 */
internal val ALL_MIGRATIONS: Array<Migration> = emptyArray()
