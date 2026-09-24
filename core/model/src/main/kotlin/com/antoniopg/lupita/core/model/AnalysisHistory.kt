package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Un resultado de herramienta guardado ("Historial"). A diferencia de [CostLogEntry] (F4, solo
 * metadatos de coste), esto guarda el TEXTO real del analisis — sin esto, cerrar el panel de
 * resultados perdia el resultado para siempre (hueco real detectado 2026-09-25). [sessionId]
 * agrupa las herramientas de una misma captura, igual que en [com.antoniopg.lupita.orchestrator.AnalysisRunner]
 * (F6: varias herramientas pueden compartir una misma captura).
 */
data class AnalysisHistoryEntry(
    val id: Long = 0,
    val sessionId: String,
    val timestampMillis: Long,
    val packageName: String,
    val appLabel: String?,
    val tool: ToolId,
    val capability: String,
    val model: String?,
    val succeeded: Boolean,
    /** Texto real del analisis — solo presente si [succeeded]. */
    val text: String?,
    /** Motivo si no [succeeded] (denegado por presupuesto, fallo del proveedor, sin clave...). */
    val reason: String?,
    val cost: CostMicros,
)

/** Contrato del historial. La implementacion (Room) vive en `:app`. */
interface AnalysisHistoryRepository {
    suspend fun record(entry: AnalysisHistoryEntry)
    fun observeRecent(limit: Int): Flow<List<AnalysisHistoryEntry>>
}
