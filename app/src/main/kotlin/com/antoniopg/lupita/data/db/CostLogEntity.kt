package com.antoniopg.lupita.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antoniopg.lupita.core.model.CostLogEntry
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ResourceClass
import com.antoniopg.lupita.core.model.ToolId

/**
 * Fila de la tabla `cost_log` (F4 paso 3, primera BD Room del proyecto). Guarda las claves de los enums
 * como `String` (mismo criterio que [com.antoniopg.lupita.data.DataStoreBudgetSettingsRepository]):
 * sobrevive a que se reordenen los `enum class` de `core:model`.
 */
@Entity(tableName = "cost_log")
data class CostLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val tool: String,
    val depth: String,
    val capability: String,
    val resourceClass: String,
    val costMicros: Long,
    val succeeded: Boolean,
    val model: String?,
)

/** `null` si el dato es antiguo/corrupto (herramienta o profundidad ya no existe) — se descarta, no crashea. */
fun CostLogEntity.toDomain(): CostLogEntry? {
    val toolId = ToolId.fromKey(tool) ?: return null
    val depthValue = Depth.fromKey(depth) ?: return null
    val resourceClassValue = runCatching { ResourceClass.valueOf(resourceClass) }.getOrNull() ?: return null
    return CostLogEntry(
        id = id,
        timestampMillis = timestampMillis,
        tool = toolId,
        depth = depthValue,
        capability = capability,
        resourceClass = resourceClassValue,
        cost = CostMicros(costMicros),
        succeeded = succeeded,
        model = model,
    )
}

fun CostLogEntry.toEntity(): CostLogEntity = CostLogEntity(
    id = id,
    timestampMillis = timestampMillis,
    tool = tool.key,
    depth = depth.key,
    capability = capability,
    resourceClass = resourceClass.name,
    costMicros = cost.value,
    succeeded = succeeded,
    model = model,
)
