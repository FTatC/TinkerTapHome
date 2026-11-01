package io.github.ftatc.tinkertaphome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices")
    fun observeAll(): Flow<List<DeviceEntity>>

    /** Sparar en ny enhet och returnerar dess id. Misslyckas om adressen redan finns. */
    @Insert
    suspend fun insert(device: DeviceEntity): Long
}
