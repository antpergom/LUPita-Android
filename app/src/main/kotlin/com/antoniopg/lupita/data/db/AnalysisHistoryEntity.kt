package com.antoniopg.lupita.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antoniopg.lupita.core.model.AnalysisHistoryEntry
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.ToolId

/** Fila de `analysis_history` — el texto real del resultado, no solo su coste (ver AnalysisHistory.kt). */
@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMillis: Long,
    val packageName: String,
    val appLabel: String?,
    val tool: String,
    val capability: String,
    val model: String?,
    val succeeded: Boolean,
    val text: String?,
    val reason: String?,
    val costMicros: Long,
)

/** `null` si el dato es antiguo/corrupto (herramienta que ya no existe) — se descarta, no crashea. */
fun AnalysisHistoryEntity.toDomain(): AnalysisHistoryEntry? {
    val toolId = ToolId.fromKey(tool) ?: return null
    return AnalysisHistoryEntry(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        packageName = packageName,
        appLabel = appLabel,
        tool = toolId,
        capability = capability,
        model = model,
        succeeded = succeeded,
        text = text,
        reason = reason,
        cost = CostMicros(costMicros),
    )
}

fun AnalysisHistoryEntry.toEntity(): AnalysisHistoryEntity = AnalysisHistoryEntity(
    id = id,
    sessionId = sessionId,
    timestampMillis = timestampMillis,
    packageName = packageName,
    appLabel = appLabel,
    tool = tool.key,
    capability = capability,
    model = model,
    succeeded = succeeded,
    text = text,
    reason = reason,
    costMicros = cost.value,
)
