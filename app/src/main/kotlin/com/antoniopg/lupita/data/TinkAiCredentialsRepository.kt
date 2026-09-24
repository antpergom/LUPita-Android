package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antoniopg.lupita.core.model.AiCredentials
import com.antoniopg.lupita.core.model.AiCredentialsRepository
import com.antoniopg.lupita.data.crypto.AeadFieldCodec
import kotlinx.coroutines.flow.map

private val KEY_API_KEY = stringPreferencesKey("ai_openai_api_key")
private const val FIELD_API_KEY = "ai_openai_api_key"

/**
 * Credenciales del proveedor de IA (F5 paso 1), cifradas con Tink — mismo mecanismo que las
 * credenciales del proyecto hermano (ver `data/crypto/AeadFieldCodec.kt`), en un `DataStore` propio
 * (no el de ajustes generales): igual que alli, separar el fichero deja la puerta abierta a un
 * "boton del panico" futuro que borre solo credenciales sin tocar el resto de la configuracion.
 *
 * Proveedor: OpenAI (GPT-6 Luna) — decision 2026-09-24, sustituye a DeepSeek (decidido 2026-09-22,
 * nunca implementado). Ver `docs/decisions/2026-09-24-f5-proveedor-openai-gpt6-luna.md`.
 */
class TinkAiCredentialsRepository(
    private val dataStore: DataStore<Preferences>,
    private val codec: AeadFieldCodec,
) : AiCredentialsRepository {
    override val credentials = dataStore.data.map { prefs ->
        val raw = prefs[KEY_API_KEY] ?: return@map null
        val apiKey = codec.decrypt(FIELD_API_KEY, raw) ?: return@map null
        AiCredentials(apiKey)
    }

    override suspend fun save(credentials: AiCredentials) {
        dataStore.edit { prefs -> prefs[KEY_API_KEY] = codec.encrypt(FIELD_API_KEY, credentials.apiKey) }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_API_KEY) }
    }
}
