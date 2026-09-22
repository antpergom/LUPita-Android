package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Una llamada de pago ya realizada (F4 paso 3): coste, quien la origino (herramienta, profundidad,
 * capacidad) y que modelo se uso, si aplica. Es el registro que el usuario pidio explicitamente que
 * quedara en un log de base de datos facilmente accesible (decision 2026-09-22, tope de gasto). `id`
 * es 0 antes de guardarse (lo asigna la implementacion, igual que cualquier fila autogenerada).
 */
data class CostLogEntry(
    val id: Long = 0,
    val timestampMillis: Long,
    val tool: ToolId,
    val depth: Depth,
    val capability: String,
    val resourceClass: ResourceClass,
    val cost: CostMicros,
    val succeeded: Boolean,
    val model: String? = null,
)

/**
 * Contrato del log de coste. La implementacion (Room) vive en `:app` — este modulo solo declara la
 * forma, igual que [BudgetSettingsRepository]. La agregacion (sumar gasto de herramienta/periodo) se
 * hace en Kotlin sobre [CostLogRepository.entriesSince], no en SQL: mantiene la logica de negocio
 * probable sin depender de que una consulta SQL agregada este bien escrita.
 */
interface CostLogRepository {
    suspend fun record(entry: CostLogEntry)

    /** Todas las entradas con `timestampMillis >= sinceMillis`, en cualquier orden. */
    suspend fun entriesSince(sinceMillis: Long): List<CostLogEntry>

    /** Las mas recientes primero — vista para un log accesible desde la app. */
    fun observeRecent(limit: Int): Flow<List<CostLogEntry>>
}

suspend fun CostLogRepository.toolSpent(tool: ToolId, sinceMillis: Long): CostMicros =
    entriesSince(sinceMillis).filter { it.tool == tool }.fold(CostMicros.ZERO) { acc, entry -> acc + entry.cost }

suspend fun CostLogRepository.periodSpent(sinceMillis: Long): CostMicros =
    entriesSince(sinceMillis).fold(CostMicros.ZERO) { acc, entry -> acc + entry.cost }
