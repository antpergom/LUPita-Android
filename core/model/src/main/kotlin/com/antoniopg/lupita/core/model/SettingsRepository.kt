package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/** Contrato de la configuracion persistente. La implementacion (DataStore) vive en `:app`. */
interface SettingsRepository {
    val settings: Flow<BubbleSettings>

    suspend fun setToolEnabled(tool: ToolId, enabled: Boolean)

    suspend fun setDepth(depth: Depth)

    suspend fun setPosition(position: BubblePosition)
}
