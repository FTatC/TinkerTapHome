package io.github.ftatc.tinkertaphome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DeviceDao {

    @Query("SELECT * FROM devices")
    fun observeAll(): Flow<List<DeviceEntity>>

    /** Sparar en ny enhet och returnerar dess id. Misslyckas om adressen redan finns. */
    @Insert
    suspend fun insert(device: DeviceEntity): Long

    /** Hämtar enheten med [address], eller `null` om ingen enhet har den adressen. */
    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun findByAddress(address: String): DeviceEntity?

    /** Byter token för enheten med [address]. Returnerar antalet ändrade rader, alltså 0 om enheten inte finns. */
    @Query("UPDATE devices SET auth_token = :token WHERE address = :address")
    suspend fun updateAuthToken(address: String, token: String?): Int
}
