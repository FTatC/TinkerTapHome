package io.github.ftatc.tinkertaphome.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * En digital utgång som användaren vill se som tryckknapp. Finns ingen rad visas utgången som switch.
 * Lades till i databasversion 3.
 *
 * Utgången identifieras med namnet, inte med id, eftersom id kan ändras mellan firmwareversioner.
 * Raden tas bort när noden tas bort.
 */
@Entity(
    tableName = "push_buttons",
    primaryKeys = ["device_address", "output_name"],
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["address"],
            childColumns = ["device_address"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class PushButtonEntity(
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    @ColumnInfo(name = "output_name") val outputName: String,
)
