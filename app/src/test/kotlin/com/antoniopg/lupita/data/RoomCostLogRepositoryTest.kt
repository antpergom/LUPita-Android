package com.antoniopg.lupita.data

import com.antoniopg.lupita.core.model.CostLogEntry
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ResourceClass
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.data.db.CostLogDao
import com.antoniopg.lupita.data.db.CostLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake en memoria de [CostLogDao] — mismo patron que las demas pruebas de repositorio de este proyecto. */
private class FakeCostLogDao : CostLogDao {
    private val rows = mutableListOf<CostLogEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: CostLogEntity): Long {
        val stored = entity.copy(id = nextId++)
        rows += stored
        return stored.id
    }

    override suspend fun entriesSince(sinceMillis: Long): List<CostLogEntity> =
        rows.filter { it.timestampMillis >= sinceMillis }

    override fun observeRecent(limit: Int): StateFlow<List<CostLogEntity>> =
        MutableStateFlow(rows.sortedByDescending { it.timestampMillis }.take(limit))
}

class RoomCostLogRepositoryTest {
    private fun entry(cost: Double = 0.05, tool: ToolId = ToolId.VERIFY, timestamp: Long = 1_000) = CostLogEntry(
        timestampMillis = timestamp,
        tool = tool,
        depth = Depth.MEDIUM,
        capability = "claim_review",
        resourceClass = ResourceClass.NETWORK,
        cost = CostMicros.ofUsd(cost),
        succeeded = true,
        model = "deepseek-chat",
    )

    @Test
    fun `record then entriesSince round-trips every field`() = runBlocking {
        val repo = RoomCostLogRepository(FakeCostLogDao())
        val original = entry()

        repo.record(original)
        val stored = repo.entriesSince(0).single()

        assertEquals(original.timestampMillis, stored.timestampMillis)
        assertEquals(original.tool, stored.tool)
        assertEquals(original.depth, stored.depth)
        assertEquals(original.capability, stored.capability)
        assertEquals(original.resourceClass, stored.resourceClass)
        assertEquals(original.cost, stored.cost)
        assertEquals(original.succeeded, stored.succeeded)
        assertEquals(original.model, stored.model)
    }

    @Test
    fun `entriesSince excludes rows older than the window`() = runBlocking {
        val repo = RoomCostLogRepository(FakeCostLogDao())
        repo.record(entry(timestamp = 100))
        repo.record(entry(timestamp = 200))

        val result = repo.entriesSince(150)

        assertEquals(1, result.size)
        assertEquals(200, result.single().timestampMillis)
    }

    @Test
    fun `a row with an unknown tool key is dropped, not crashed on`() = runBlocking {
        val dao = FakeCostLogDao()
        dao.insert(
            CostLogEntity(
                timestampMillis = 1,
                tool = "un_id_que_ya_no_existe",
                depth = Depth.LOW.key,
                capability = "x",
                resourceClass = ResourceClass.DETERMINISTIC.name,
                costMicros = 1,
                succeeded = true,
                model = null,
            ),
        )
        val repo = RoomCostLogRepository(dao)

        assertTrue(repo.entriesSince(0).isEmpty())
    }

    @Test
    fun `observeRecent maps and orders through the dao as-is`() = runBlocking {
        val repo = RoomCostLogRepository(FakeCostLogDao())
        repo.record(entry(timestamp = 100))
        repo.record(entry(timestamp = 300))
        repo.record(entry(timestamp = 200))

        val timestamps = repo.observeRecent(2).first().map(CostLogEntry::timestampMillis)

        assertEquals(listOf(300L, 200L), timestamps)
    }
}
