package com.secretarrow.sineminer.data.repo

import com.secretarrow.sineminer.data.local.EntityMiningSession
import com.secretarrow.sineminer.data.local.dao.MiningSessionDao
import com.secretarrow.sineminer.data.model.MiningStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mining repository — tracks mining sessions and aggregate stats.
 *
 * The actual mining engine lives in [com.secretarrow.sineminer.service.MiningForegroundService];
 * this repository is only for persisted stats and session history.
 */
@Singleton
class MiningRepository @Inject constructor(
    private val sessionDao: MiningSessionDao,
) {
    fun observeRecentSessions() = sessionDao.observeRecent()
    fun observeActiveSession() = sessionDao.observeActive()
    fun observeTotalHashes() = sessionDao.observeTotalHashes()
    fun observeSharesAccepted() = sessionDao.observeSharesAccepted()
    fun observeSharesRejected() = sessionDao.observeSharesRejected()
    fun observeBlocksFound() = sessionDao.observeBlocksFound()

    /** Aggregate stats across all sessions. */
    fun observeAggregateStats(): Flow<MiningAggregate> = combine(
        sessionDao.observeTotalHashes(),
        sessionDao.observeSharesAccepted(),
        sessionDao.observeSharesRejected(),
        sessionDao.observeBlocksFound(),
    ) { total, accepted, rejected, blocks ->
        MiningAggregate(
            totalHashes = total,
            sharesAccepted = accepted,
            sharesRejected = rejected,
            blocksFound = blocks,
        )
    }

    /** Begin a new session and return its id. */
    suspend fun startSession(threads: Int, memoryKib: Long, poolHost: String?, poolPort: Int?): Long {
        return sessionDao.insert(
            EntityMiningSession(
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                totalHashes = 0,
                sharesAccepted = 0,
                sharesRejected = 0,
                blocksFound = 0,
                threads = threads,
                memoryKib = memoryKib,
                poolHost = poolHost,
                poolPort = poolPort,
            )
        )
    }

    /** Mark a session as ended with final stats. */
    suspend fun endSession(id: Long, totalHashes: Long, accepted: Int, rejected: Int, blocks: Int) {
        sessionDao.updateOnStop(id, System.currentTimeMillis(), totalHashes, accepted, rejected, blocks)
    }

    suspend fun clearHistory() = sessionDao.clear()
}

data class MiningAggregate(
    val totalHashes: Long,
    val sharesAccepted: Int,
    val sharesRejected: Int,
    val blocksFound: Int,
)
