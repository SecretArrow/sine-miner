package com.secretarrow.sineminer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretarrow.sineminer.data.local.EntityLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<EntityLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntityLogEntry): Long

    @Query("DELETE FROM log_entries")
    suspend fun clear()

    @Query("DELETE FROM log_entries WHERE id < (SELECT MIN(id) FROM (SELECT id FROM log_entries ORDER BY timestamp DESC LIMIT 500))")
    suspend fun trim()
}
