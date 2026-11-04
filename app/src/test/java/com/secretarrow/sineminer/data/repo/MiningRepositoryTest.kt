package com.secretarrow.sineminer.data.repo

import app.cash.turbine.test
import com.secretarrow.sineminer.data.local.EntityMiningSession
import com.secretarrow.sineminer.data.local.dao.MiningSessionDao
import com.secretarrow.sineminer.data.model.PoolConfig
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MiningRepositoryTest {

    @Test
    fun `aggregate stats combine all sources`() = runTest {
        val dao = mock<MiningSessionDao>()
        whenever(dao.observeTotalHashes()).thenReturn(flowOf(1000L))
        whenever(dao.observeSharesAccepted()).thenReturn(flowOf(10))
        whenever(dao.observeSharesRejected()).thenReturn(flowOf(2))
        whenever(dao.observeBlocksFound()).thenReturn(flowOf(1))
        val repo = MiningRepository(dao)
        repo.observeAggregateStats().test {
            val agg = awaitItem()
            assertEquals(1000L, agg.totalHashes)
            assertEquals(10, agg.sharesAccepted)
            assertEquals(2, agg.sharesRejected)
            assertEquals(1, agg.blocksFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `start session inserts new row`() = runTest {
        val dao = mock<MiningSessionDao>()
        whenever(dao.insert(any())).thenReturn(42L)
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

    @Test
    fun `start session passes pool host and port`() = runTest {
        val dao = mock<MiningSessionDao>()
        whenever(dao.insert(any())).thenReturn(1L)
        val repo = MiningRepository(dao)
        repo.startSession(threads = 4, memoryKib = 2_097_152, poolHost = "eu.bntpool.com", poolPort = 4433)
        val captor = argumentCaptor<EntityMiningSession>()
        verify(dao).insert(captor.capture())
        assertEquals(4, captor.firstValue.threads)
        assertEquals(2_097_152L, captor.firstValue.memoryKib)
        assertEquals("eu.bntpool.com", captor.firstValue.poolHost)
        assertEquals(4433, captor.firstValue.poolPort)
    }
}
