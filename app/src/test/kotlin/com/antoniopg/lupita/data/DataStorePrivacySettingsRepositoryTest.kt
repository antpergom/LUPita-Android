package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.UserRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class PrivacyInMemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class DataStorePrivacySettingsRepositoryTest {

    private val store = PrivacyInMemoryStore()
    private val defaults = setOf("GLOBAL", "ES")
    private val repo = DataStorePrivacySettingsRepository(store, defaults)

    @Test
    fun `first launch gives the safe factory settings`() = runTest {
        val s = repo.settings.first()

        assertEquals(PrivacySettings(enabledRegions = defaults), s)
        assertEquals(PrivacyTier.SENSITIVE, s.unknownAppTier)
        SecurityMeasure.entries.forEach { assertTrue(s.isEnabled(it)) }
    }

    @Test
    fun `a measure can be switched off and back on, and untouched ones keep their default`() = runTest {
        repo.setMeasure(SecurityMeasure.AUDIT_LOG, false)

        val off = repo.settings.first()
        assertFalse(off.isEnabled(SecurityMeasure.AUDIT_LOG))
        assertTrue(off.isEnabled(SecurityMeasure.REDACT_PATTERNS))

        repo.setMeasure(SecurityMeasure.AUDIT_LOG, true)
        assertTrue(repo.settings.first().isEnabled(SecurityMeasure.AUDIT_LOG))
    }

    @Test
    fun `the level for unknown apps is remembered`() = runTest {
        repo.setUnknownAppTier(PrivacyTier.PROTECTED)

        assertEquals(PrivacyTier.PROTECTED, repo.settings.first().unknownAppTier)
    }

    @Test
    fun `a corrupt stored level falls back to SENSITIVE - it must never relax protection`() = runTest {
        store.edit { it[stringPreferencesKey("privacy_unknown_tier")] = "whatever" }

        assertEquals(PrivacyTier.SENSITIVE, repo.settings.first().unknownAppTier)
    }

    @Test
    fun `a group level can be overridden and reset`() = runTest {
        repo.setGroupTier("social", PrivacyTier.SENSITIVE)
        repo.setGroupTier("banking", PrivacyTier.NORMAL)
        assertEquals(mapOf("social" to PrivacyTier.SENSITIVE, "banking" to PrivacyTier.NORMAL), repo.settings.first().groupTiers)

        repo.setGroupTier("banking", null)
        assertEquals(mapOf("social" to PrivacyTier.SENSITIVE), repo.settings.first().groupTiers)
    }

    @Test
    fun `changing a group level replaces it instead of duplicating it`() = runTest {
        repo.setGroupTier("social", PrivacyTier.SENSITIVE)
        repo.setGroupTier("social", PrivacyTier.PROTECTED)

        assertEquals(mapOf("social" to PrivacyTier.PROTECTED), repo.settings.first().groupTiers)
    }

    @Test
    fun `regions start from the defaults and toggling one does not lose the others`() = runTest {
        repo.setRegionEnabled("ES", false)
        assertEquals(setOf("GLOBAL"), repo.settings.first().enabledRegions)

        repo.setRegionEnabled("US", true)
        assertEquals(setOf("GLOBAL", "US"), repo.settings.first().enabledRegions)
    }

    @Test
    fun `user rules can be added, replaced by app and removed`() = runTest {
        val bank = AppMatch.Exact("com.mi.banco")
        repo.putUserRule(UserRule(bank, PrivacyTier.PROTECTED))
        repo.putUserRule(UserRule(AppMatch.Prefix("com.acme"), PrivacyTier.NORMAL))
        assertEquals(2, repo.settings.first().userRules.size)

        repo.putUserRule(UserRule(bank, PrivacyTier.SENSITIVE)) // misma app: sustituye
        val rules = repo.settings.first().userRules
        assertEquals(2, rules.size)
        assertEquals(PrivacyTier.SENSITIVE, rules.first { it.match == bank }.tier)

        repo.removeUserRule(bank)
        assertEquals(listOf(AppMatch.Prefix("com.acme")), repo.settings.first().userRules.map { it.match })
    }

    @Test
    fun `corrupt stored rules are ignored without losing the good ones`() = runTest {
        store.edit { it[stringSetPreferencesKey("privacy_user_rules")] = setOf("garbage", "protected|exact:com.ok", "weird|exact:x") }

        assertEquals(listOf(UserRule(AppMatch.Exact("com.ok"), PrivacyTier.PROTECTED)), repo.settings.first().userRules)
    }

    @Test
    fun `everything survives an app restart`() = runTest {
        repo.setMeasure(SecurityMeasure.PREVIEW_BEFORE_SEND, false)
        repo.setUnknownAppTier(PrivacyTier.PROTECTED)
        repo.setGroupTier("social", PrivacyTier.SENSITIVE)
        repo.setRegionEnabled("ES", false)
        repo.putUserRule(UserRule(AppMatch.Exact("com.mi.banco"), PrivacyTier.PROTECTED))

        val after = DataStorePrivacySettingsRepository(store, defaults).settings.first()

        assertFalse(after.isEnabled(SecurityMeasure.PREVIEW_BEFORE_SEND))
        assertEquals(PrivacyTier.PROTECTED, after.unknownAppTier)
        assertEquals(mapOf("social" to PrivacyTier.SENSITIVE), after.groupTiers)
        assertEquals(setOf("GLOBAL"), after.enabledRegions)
        assertEquals(1, after.userRules.size)
    }
}
