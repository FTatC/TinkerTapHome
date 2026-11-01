package io.github.ftatc.tinkertaphome.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
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

/**
 * Mappar en rad till domänmodellen. Returnerar `null` om adressen i databasen inte är en giltig
 * BLE-adress. Appen skriver bara normaliserade adresser, så det ska inte hända, men en trasig rad
 * får inte göra att hela listan misslyckas.
 */
internal fun DeviceEntity.toDomain(): Device? =
    BleAddress.parse(address)?.let { Device(id = id, address = it, name = name) }
