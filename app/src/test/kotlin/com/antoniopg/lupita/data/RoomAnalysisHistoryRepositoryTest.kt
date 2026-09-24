package com.antoniopg.lupita.data

import com.antoniopg.lupita.core.model.AnalysisHistoryEntry
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.data.db.AnalysisHistoryDao
import com.antoniopg.lupita.data.db.AnalysisHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake en memoria de [AnalysisHistoryDao] — mismo patron que [RoomCostLogRepositoryTest]. */
private class FakeAnalysisHistoryDao : AnalysisHistoryDao {
    private val rows = mutableListOf<AnalysisHistoryEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: AnalysisHistoryEntity): Long {
        val stored = entity.copy(id = nextId++)
        rows += stored
        return stored.id
    }

    override fun observeRecent(limit: Int): StateFlow<List<AnalysisHistoryEntity>> =
        MutableStateFlow(rows.sortedByDescending { it.timestampMillis }.take(limit))
}

class RoomAnalysisHistoryRepositoryTest {
    private fun entry(
        succeeded: Boolean = true,
        text: String? = "el analisis dice...",
        reason: String? = null,
        timestamp: Long = 1_000,
    ) = AnalysisHistoryEntry(
        sessionId = "session-1",
        timestampMillis = timestamp,
        packageName = "com.twitter.android",
        appLabel = "X",
        tool = ToolId.GENERAL,
        capability = "general_analysis_v1",
        model = "gpt-6-luna",
        succeeded = succeeded,
        text = text,
        reason = reason,
        cost = CostMicros.ofUsd(0.0001),
    )

    @Test
    fun `record then observeRecent round-trips every field of a success`() = runBlocking {
        val repo = RoomAnalysisHistoryRepository(FakeAnalysisHistoryDao())
        val original = entry()

        repo.record(original)
        val stored = repo.observeRecent(10).first().single()

        assertEquals(original.sessionId, stored.sessionId)
        assertEquals(original.timestampMillis, stored.timestampMillis)
        assertEquals(original.packageName, stored.packageName)
        assertEquals(original.appLabel, stored.appLabel)
        assertEquals(original.tool, stored.tool)
        assertEquals(original.capability, stored.capability)
        assertEquals(original.model, stored.model)
        assertEquals(original.succeeded, stored.succeeded)
        assertEquals(original.text, stored.text)
        assertEquals(original.reason, stored.reason)
        assertEquals(original.cost, stored.cost)
    }

    @Test
    fun `a failed entry keeps the reason and drops the text`() = runBlocking {
        val repo = RoomAnalysisHistoryRepository(FakeAnalysisHistoryDao())
        repo.record(entry(succeeded = false, text = null, reason = "tope de coste global alcanzado"))

        val stored = repo.observeRecent(10).first().single()

        assertEquals(false, stored.succeeded)
        assertEquals(null, stored.text)
        assertEquals("tope de coste global alcanzado", stored.reason)
    }

    @Test
    fun `a row with an unknown tool key is dropped, not crashed on`() = runBlocking {
        val dao = FakeAnalysisHistoryDao()
        dao.insert(
            AnalysisHistoryEntity(
                sessionId = "s",
                timestampMillis = 1,
                packageName = "x",
                appLabel = null,
                tool = "un_id_que_ya_no_existe",
                capability = "x",
                model = null,
                succeeded = true,
                text = "x",
                reason = null,
                costMicros = 1,
            ),
        )
        val repo = RoomAnalysisHistoryRepository(dao)

        assertTrue(repo.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `observeRecent orders newest first and honors the limit`() = runBlocking {
        val repo = RoomAnalysisHistoryRepository(FakeAnalysisHistoryDao())
        repo.record(entry(timestamp = 100))
        repo.record(entry(timestamp = 300))
        repo.record(entry(timestamp = 200))

        val timestamps = repo.observeRecent(2).first().map(AnalysisHistoryEntry::timestampMillis)

        assertEquals(listOf(300L, 200L), timestamps)
    }
}
