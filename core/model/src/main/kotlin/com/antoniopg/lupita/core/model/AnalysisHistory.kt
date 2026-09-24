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

/** Una captura, con las herramientas que se le aplicaron — una tarjeta de "Historial" por captura. */
data class AnalysisSession(
    val sessionId: String,
    val timestampMillis: Long,
    val packageName: String,
    val appLabel: String?,
    val tools: List<ToolId>,
)

/**
 * Agrupa entradas planas (una por herramienta) en sesiones (una por captura), mas recientes
 * primero. Puro — sin formateo de fecha ni etiquetas localizadas, eso es cosa de la UI.
 */
fun List<AnalysisHistoryEntry>.groupedBySession(): List<AnalysisSession> {
    val bySession = LinkedHashMap<String, MutableList<AnalysisHistoryEntry>>()
    forEach { bySession.getOrPut(it.sessionId) { mutableListOf() }.add(it) }
    return bySession.map { (sessionId, entries) ->
        val newest = entries.maxBy { it.timestampMillis }
        AnalysisSession(
            sessionId = sessionId,
            timestampMillis = newest.timestampMillis,
            packageName = newest.packageName,
            appLabel = newest.appLabel,
            tools = entries.sortedBy { ToolId.entries.indexOf(it.tool) }.map { it.tool },
        )
    }.sortedByDescending { it.timestampMillis }
}
