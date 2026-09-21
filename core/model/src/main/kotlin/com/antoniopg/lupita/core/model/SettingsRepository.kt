package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/** Contrato de la configuracion persistente. La implementacion (DataStore) vive en `:app`. */
interface SettingsRepository {
    val settings: Flow<BubbleSettings>

    /** Id del modelo de IA elegido en Ajustes; `null` si el usuario aun no ha elegido. */
    val modelId: Flow<String?>

    suspend fun setModelId(id: String)

    suspend fun setToolEnabled(tool: ToolId, enabled: Boolean)

    suspend fun setDepth(depth: Depth)

    suspend fun setPosition(position: BubblePosition)
}
