package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antoniopg.lupita.core.model.BudgetPeriod
import com.antoniopg.lupita.core.model.BudgetSettings
import com.antoniopg.lupita.core.model.BudgetSettingsRepository
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.DepthBudget
import com.antoniopg.lupita.core.model.ToolId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Configuracion de presupuesto en DataStore. Solo se guardan los topes que el usuario ha TOCADO; los
 * ausentes valen lo que diga `BudgetDefaults` (el catalogo de fabrica) — asi que restablecer de fabrica es
 * simplemente borrar estas claves, no copiar numeros.
 */
class DataStoreBudgetSettingsRepository(private val dataStore: DataStore<Preferences>) : BudgetSettingsRepository {

    override val settings: Flow<BudgetSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            BudgetSettings(
                depthBudgets = Depth.entries.mapNotNull { depth ->
                    val cost = prefs[costKey(depth)] ?: return@mapNotNull null
                    val calls = prefs[callsKey(depth)] ?: return@mapNotNull null
                    depth to DepthBudget(CostMicros(cost), calls)
                }.toMap(),
                toolLimits = ToolId.entries.mapNotNull { tool ->
                    prefs[toolKey(tool)]?.let { tool to CostMicros(it) }
                }.toMap(),
                globalLimit = prefs[GLOBAL_LIMIT]?.let(::CostMicros),
                globalPeriod = BudgetPeriod.fromKey(prefs[GLOBAL_PERIOD]) ?: BudgetPeriod.DEFAULT,
            )
        }

    override suspend fun setDepthBudget(depth: Depth, budget: DepthBudget) {
        dataStore.edit {
            it[costKey(depth)] = budget.costLimit.value
            it[callsKey(depth)] = budget.maxPaidCalls
        }
    }

    override suspend fun setToolLimit(tool: ToolId, limit: CostMicros) {
        dataStore.edit { it[toolKey(tool)] = limit.value }
    }

    override suspend fun setGlobalLimit(limit: CostMicros) {
        dataStore.edit { it[GLOBAL_LIMIT] = limit.value }
    }

    override suspend fun setGlobalPeriod(period: BudgetPeriod) {
        dataStore.edit { it[GLOBAL_PERIOD] = period.key }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            Depth.entries.forEach { prefs.remove(costKey(it)); prefs.remove(callsKey(it)) }
            ToolId.entries.forEach { prefs.remove(toolKey(it)) }
            prefs.remove(GLOBAL_LIMIT)
            prefs.remove(GLOBAL_PERIOD)
        }
    }

    private fun costKey(depth: Depth) = longPreferencesKey("budget_depth_cost_${depth.key}")
    private fun callsKey(depth: Depth) = intPreferencesKey("budget_depth_calls_${depth.key}")
    private fun toolKey(tool: ToolId) = longPreferencesKey("budget_tool_${tool.key}")

    private companion object {
        val GLOBAL_LIMIT = longPreferencesKey("budget_global_limit")
        val GLOBAL_PERIOD = stringPreferencesKey("budget_global_period")
    }
}
