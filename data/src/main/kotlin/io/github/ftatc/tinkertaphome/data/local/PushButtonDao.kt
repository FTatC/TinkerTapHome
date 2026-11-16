package io.github.ftatc.tinkertaphome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PushButtonDao {

    /** Strömmar namnen på utgångarna som är tryckknappar på noden med [address]. */
    @Query("SELECT output_name FROM push_buttons WHERE device_address = :address")
    fun observeOutputNames(address: String): Flow<List<String>>

    /**
     * Markerar utgången som tryckknapp. Gör inget om den redan är det. Misslyckas med
     * `SQLiteConstraintException` om noden inte är sparad.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pushButton: PushButtonEntity)

    /** Gör utgången till en switch igen. Gör inget om den redan är det. */
    @Query("DELETE FROM push_buttons WHERE device_address = :address AND output_name = :outputName")
    suspend fun delete(address: String, outputName: String)
}
