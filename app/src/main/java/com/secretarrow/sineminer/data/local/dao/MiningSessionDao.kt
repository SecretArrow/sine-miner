package com.secretarrow.sineminer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretarrow.sineminer.data.local.EntityMiningSession
import kotlinx.coroutines.flow.Flow

@Dao
interface MiningSessionDao {
    @Query("SELECT * FROM mining_sessions ORDER BY startedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<EntityMiningSession>>

    @Query("SELECT * FROM mining_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<EntityMiningSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: EntityMiningSession): Long

    @Query("UPDATE mining_sessions SET endedAt = :endedAt, totalHashes = :totalHashes, sharesAccepted = :accepted, sharesRejected = :rejected, blocksFound = :blocks WHERE id = :id")
    suspend fun updateOnStop(id: Long, endedAt: Long, totalHashes: Long, accepted: Int, rejected: Int, blocks: Int)

    @Query("SELECT COALESCE(SUM(totalHashes), 0) FROM mining_sessions")
    fun observeTotalHashes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(sharesAccepted), 0) FROM mining_sessions")
    fun observeSharesAccepted(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sharesRejected), 0) FROM mining_sessions")
    fun observeSharesRejected(): Flow<Int>

    @Query("SELECT COALESCE(SUM(blocksFound), 0) FROM mining_sessions")
    fun observeBlocksFound(): Flow<Int>

    @Query("DELETE FROM mining_sessions")
    suspend fun clear()
}
