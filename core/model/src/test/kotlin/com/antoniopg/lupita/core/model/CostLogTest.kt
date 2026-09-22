package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeCostLogRepository(private val entries: List<CostLogEntry>) : CostLogRepository {
    override suspend fun record(entry: CostLogEntry) = error("no usado en este test")
    override suspend fun entriesSince(sinceMillis: Long): List<CostLogEntry> = entries.filter { it.timestampMillis >= sinceMillis }
    override fun observeRecent(limit: Int): StateFlow<List<CostLogEntry>> = MutableStateFlow(entries.take(limit))
}

class CostLogTest {
    private fun entry(tool: ToolId, timestamp: Long, usd: Double) = CostLogEntry(
        timestampMillis = timestamp,
        tool = tool,
        depth = Depth.LOW,
        capability = "test_capability",
        resourceClass = ResourceClass.DETERMINISTIC,
        cost = CostMicros.ofUsd(usd),
        succeeded = true,
    )

    @Test
    fun `toolSpent sums only entries for that tool within the window`() = runBlocking {
        val repo = FakeCostLogRepository(
            listOf(
                entry(ToolId.VERIFY, timestamp = 100, usd = 0.10),
                entry(ToolId.VERIFY, timestamp = 200, usd = 0.05),
                entry(ToolId.GENERAL, timestamp = 150, usd = 1.00),
                entry(ToolId.VERIFY, timestamp = 50, usd = 0.20), // fuera de la ventana
            ),
        )

        assertEquals(CostMicros.ofUsd(0.15), repo.toolSpent(ToolId.VERIFY, sinceMillis = 100))
    }

    @Test
    fun `periodSpent sums every entry within the window regardless of tool`() = runBlocking {
        val repo = FakeCostLogRepository(
            listOf(
                entry(ToolId.VERIFY, timestamp = 100, usd = 0.10),
                entry(ToolId.GENERAL, timestamp = 150, usd = 1.00),
                entry(ToolId.AI_DETECT, timestamp = 50, usd = 0.20), // fuera de la ventana
            ),
        )

        assertEquals(CostMicros.ofUsd(1.10), repo.periodSpent(sinceMillis = 100))
    }

    @Test
    fun `an empty window sums to zero`() = runBlocking {
        val repo = FakeCostLogRepository(emptyList())

        assertEquals(CostMicros.ZERO, repo.periodSpent(sinceMillis = 0))
        assertEquals(CostMicros.ZERO, repo.toolSpent(ToolId.GENERAL, sinceMillis = 0))
    }
}
