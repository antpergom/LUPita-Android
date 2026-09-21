package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoniopg.lupita.core.model.BubblePosition
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.core.model.ToolId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Configuracion en DataStore Preferences. Se persisten las CLAVES estables de `ToolId`/`Depth`, no
 * los nombres del enum. Un dato desconocido o corrupto se ignora y cae al valor por defecto: nunca
 * debe tumbar la app.
 */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    // Fichero ilegible: se trata como configuracion de fabrica en vez de fallar.
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    override val modelId: Flow<String?> = safeData.map { it[MODEL_ID] }

    override suspend fun setModelId(id: String) {
        dataStore.edit { it[MODEL_ID] = id }
    }

    override val settings: Flow<BubbleSettings> = safeData
        .map { prefs ->
            BubbleSettings(
                enabledTools = prefs[ENABLED_TOOLS].orEmpty().mapNotNull(ToolId::fromKey).toSet(),
                depth = prefs[DEPTH]?.let(Depth::fromKey) ?: Depth.DEFAULT,
                position = prefs[POSITION_X]?.let { x -> prefs[POSITION_Y]?.let { y -> BubblePosition(x, y) } },
            )
        }

    override suspend fun setToolEnabled(tool: ToolId, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[ENABLED_TOOLS].orEmpty()
            prefs[ENABLED_TOOLS] = if (enabled) current + tool.key else current - tool.key
        }
    }

    override suspend fun setDepth(depth: Depth) {
        dataStore.edit { it[DEPTH] = depth.key }
    }

    override suspend fun setPosition(position: BubblePosition) {
        dataStore.edit {
            it[POSITION_X] = position.x
            it[POSITION_Y] = position.y
        }
    }

    private companion object {
        val ENABLED_TOOLS = stringSetPreferencesKey("enabled_tools")
        val DEPTH = stringPreferencesKey("depth")
        val MODEL_ID = stringPreferencesKey("model_id")
        val POSITION_X = intPreferencesKey("bubble_x")
        val POSITION_Y = intPreferencesKey("bubble_y")
    }
}
