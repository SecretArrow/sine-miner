package com.secretarrow.sineminer.data.repo

import com.secretarrow.sineminer.data.local.EntityMiningSession
import com.secretarrow.sineminer.data.local.dao.MiningSessionDao
import com.secretarrow.sineminer.data.model.PoolConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MiningRepositoryTest {

    @Test
    fun `aggregate stats combine all sources`() = runTest {
        val dao = mock<MiningSessionDao> {
            on { observeTotalHashes() } doReturn flowOf(1000L)
            on { observeSharesAccepted() } doReturn flowOf(10)
            on { observeSharesRejected() } doReturn flowOf(2)
            on { observeBlocksFound() } doReturn flowOf(1)
        }
        val repo = MiningRepository(dao)
        val agg = repo.observeAggregateStats().first()
        assertEquals(1000L, agg.totalHashes)
        assertEquals(10, agg.sharesAccepted)
        assertEquals(2, agg.sharesRejected)
        assertEquals(1, agg.blocksFound)
    }

    @Test
    fun `start session inserts new row`() = runTest {
        val dao = mock<MiningSessionDao> {
            onBlocking { insert(any()) } doReturn 42L
        }
        val repo = MiningRepository(dao)
        val id = repo.startSession(threads = 2, memoryKib = 1024 * 1024, poolHost = "bntpool.com", poolPort = 3333)
        assertEquals(42L, id)
        verify(dao, times(1)).insert(any())
    }

    @Test
    fun `end session calls update with final stats`() = runTest {
        val dao = mock<MiningSessionDao>()
        val repo = MiningRepository(dao)
        repo.endSession(id = 1, totalHashes = 5000L, accepted = 5, rejected = 0, blocks = 0)
        verify(dao, times(1)).updateOnStop(eq(1L), any(), eq(5000L), eq(5), eq(0), eq(0))
    }

    @Test
    fun `clear history calls dao clear`() = runTest {
        val dao = mock<MiningSessionDao>()
        val repo = MiningRepository(dao)
        repo.clearHistory()
        verify(dao, times(1)).clear()
    }

    @Test
    fun `default pool config matches defaults`() {
        val cfg = PoolConfig.DEFAULT
        assertEquals("bntpool.com", cfg.host)
        assertEquals(3333, cfg.port)
        assertEquals("x", cfg.password)
    }
}

// Helper: enable `onBlocking` syntactic sugar for Kotlin coroutines + Mockito
infix fun <T> org.mockito.kotlin.KStubbing<T>.onBlocking(stubbing: suspend T.() -> Unit) =
    org.mockito.kotlin.coAnswers<T, Unit> { stubbing(this) }

private infix fun <T> T.doReturn(value: Any?): Any = org.mockito.kotlin.doReturn(value) as T
