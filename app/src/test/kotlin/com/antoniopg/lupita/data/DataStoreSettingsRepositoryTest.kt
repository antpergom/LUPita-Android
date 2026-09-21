package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoniopg.lupita.core.model.BubblePosition
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ToolId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** DataStore en memoria: el de verdad necesita un fichero y un Context. */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class DataStoreSettingsRepositoryTest {

    private val store = InMemoryPreferencesDataStore()
    private val repo = DataStoreSettingsRepository(store)

    @Test
    fun `first launch gives the factory defaults`() = runTest {
        assertEquals(BubbleSettings(), repo.settings.first())
    }

    @Test
    fun `enabled tools and depth survive a round trip`() = runTest {
        repo.setToolEnabled(ToolId.VERIFY, true)
        repo.setToolEnabled(ToolId.ENTITY, true)
        repo.setDepth(Depth.HIGH)

        val s = repo.settings.first()
        assertEquals(setOf(ToolId.VERIFY, ToolId.ENTITY), s.enabledTools)
        assertEquals(Depth.HIGH, s.depth)
    }

    @Test
    fun `disabling the last tool leaves the bubble gray again`() = runTest {
        repo.setToolEnabled(ToolId.GENERAL, true)
        repo.setToolEnabled(ToolId.GENERAL, false)

        val s = repo.settings.first()
        assertEquals(emptySet<ToolId>(), s.enabledTools)
        assertEquals(false, s.canCapture)
    }

    @Test
    fun `no model is chosen on first launch`() = runTest {
        assertEquals(null, repo.modelId.first())
    }

    @Test
    fun `the chosen model is remembered and survives an app restart`() = runTest {
        repo.setModelId("gemini-2-5-pro")

        assertEquals("gemini-2-5-pro", repo.modelId.first())
        assertEquals("gemini-2-5-pro", DataStoreSettingsRepository(store).modelId.first())
    }

    @Test
    fun `choosing a model does not disturb the bubble settings`() = runTest {
        repo.setToolEnabled(ToolId.GENERAL, true)
        repo.setModelId("x")

        assertEquals(setOf(ToolId.GENERAL), repo.settings.first().enabledTools)
    }

    @Test
    fun `position is remembered`() = runTest {
        repo.setPosition(BubblePosition(120, 840))

        assertEquals(BubblePosition(120, 840), repo.settings.first().position)
    }

    @Test
    fun `a new repository over the same store sees what was saved - it survives an app restart`() = runTest {
        repo.setToolEnabled(ToolId.AI_DETECT, true)
        repo.setDepth(Depth.MEDIUM)

        val afterRestart = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setOf(ToolId.AI_DETECT), afterRestart.enabledTools)
        assertEquals(Depth.MEDIUM, afterRestart.depth)
    }

    @Test
    fun `an unknown tool key or depth is ignored instead of crashing`() = runTest {
        store.edit {
            it[stringSetPreferencesKey("enabled_tools")] = setOf("general", "removed_tool")
            it[stringPreferencesKey("depth")] = "extreme"
        }

        val s = repo.settings.first()
        assertEquals(setOf(ToolId.GENERAL), s.enabledTools)
        assertEquals(Depth.DEFAULT, s.depth)
    }

    @Test
    fun `a half-written position counts as no position`() = runTest {
        store.edit { it[androidx.datastore.preferences.core.intPreferencesKey("bubble_x")] = 10 }

        assertEquals(null, repo.settings.first().position)
    }
}
