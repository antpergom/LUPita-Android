package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antoniopg.lupita.core.model.AiCredentials
import com.antoniopg.lupita.data.crypto.AeadFieldCodec
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class AiCredentialsInMemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** JUnit puro, sin Robolectric ni Android Keystore — mismo patron que `AeadFieldCodecTest` del hermano. */
private fun softwareAead() = run {
    AeadConfig.register()
    KeysetHandle.generateNew(KeyTemplates.get("AES128_GCM")).getPrimitive(RegistryConfiguration.get(), com.google.crypto.tink.Aead::class.java)
}

class TinkAiCredentialsRepositoryTest {
    private val store = AiCredentialsInMemoryStore()
    private val repo = TinkAiCredentialsRepository(store, AeadFieldCodec(softwareAead()))

    @Test
    fun `no key saved yet is null`() = runTest {
        assertNull(repo.credentials.first())
    }

    @Test
    fun `a saved key round-trips through encryption`() = runTest {
        repo.save(AiCredentials("sk-test-123"))

        assertEquals(AiCredentials("sk-test-123"), repo.credentials.first())
    }

    @Test
    fun `the stored value is never the plaintext key`() = runTest {
        repo.save(AiCredentials("sk-super-secret"))

        val raw = store.data.first()[stringPreferencesKey("ai_deepseek_api_key")]
        assertEquals(false, raw?.contains("sk-super-secret"))
    }

    @Test
    fun `clear removes the key entirely`() = runTest {
        repo.save(AiCredentials("sk-test-123"))
        repo.clear()

        assertNull(repo.credentials.first())
    }

    @Test
    fun `saving again overwrites the previous key`() = runTest {
        repo.save(AiCredentials("sk-old"))
        repo.save(AiCredentials("sk-new"))

        assertEquals(AiCredentials("sk-new"), repo.credentials.first())
    }
}
