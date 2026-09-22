package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.antoniopg.lupita.core.model.BudgetPeriod
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.DepthBudget
import com.antoniopg.lupita.core.model.ToolId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class BudgetInMemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class DataStoreBudgetSettingsRepositoryTest {

    private val store = BudgetInMemoryStore()
    private val repo = DataStoreBudgetSettingsRepository(store)

    @Test
    fun `first launch has nothing overridden, so every default applies`() = runTest {
        val s = repo.settings.first()

        assertTrue(s.depthBudgets.isEmpty())
        assertTrue(s.toolLimits.isEmpty())
        assertNull(s.globalLimit)
        assertEquals(BudgetPeriod.MONTHLY, s.globalPeriod)
    }

    @Test
    fun `a depth budget is remembered whole - cost and calls together`() = runTest {
        repo.setDepthBudget(Depth.HIGH, DepthBudget(CostMicros.ofUsd(2.0), maxPaidCalls = 15))

        val budget = repo.settings.first().depthBudgets.getValue(Depth.HIGH)
        assertEquals(CostMicros.ofUsd(2.0), budget.costLimit)
        assertEquals(15, budget.maxPaidCalls)
    }

    @Test
    fun `a tool limit and the global cap are remembered independently`() = runTest {
        repo.setToolLimit(ToolId.VERIFY, CostMicros.ofUsd(3.0))
        repo.setGlobalLimit(CostMicros.ofUsd(30.0))
        repo.setGlobalPeriod(BudgetPeriod.DAILY)

        val s = repo.settings.first()
        assertEquals(CostMicros.ofUsd(3.0), s.toolLimits[ToolId.VERIFY])
        assertEquals(CostMicros.ofUsd(30.0), s.globalLimit)
        assertEquals(BudgetPeriod.DAILY, s.globalPeriod)
    }

    @Test
    fun `resetting to defaults clears every override, not just some`() = runTest {
        repo.setDepthBudget(Depth.LOW, DepthBudget(CostMicros.ofUsd(1.0), 5))
        repo.setToolLimit(ToolId.GENERAL, CostMicros.ofUsd(9.0))
        repo.setGlobalLimit(CostMicros.ofUsd(100.0))

        repo.resetToDefaults()

        val s = repo.settings.first()
        assertTrue(s.depthBudgets.isEmpty())
        assertTrue(s.toolLimits.isEmpty())
        assertNull(s.globalLimit)
    }

    @Test
    fun `a half-corrupt depth entry - only cost or only calls stored - falls back entirely for that depth`() = runTest {
        // Simula un dato a medio escribir: solo el coste, sin el numero de llamadas.
        store.updateData { it.toMutablePreferences().apply { this[androidx.datastore.preferences.core.longPreferencesKey("budget_depth_cost_low")] = 1L }.toPreferences() }

        assertTrue(repo.settings.first().depthBudgets.isEmpty())
    }
}
