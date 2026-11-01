package io.github.ftatc.tinkertaphome.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ftatc.tinkertaphome.domain.model.Device

@Entity(
    tableName = "devices",
    indices = [Index(value = ["address"], unique = true)],
)
internal data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "name") val name: String,
)

internal fun DeviceEntity.toDomain(): Device = Device(id = id, address = address, name = name)
