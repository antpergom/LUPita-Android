package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsTest {

    @Test
    fun `persisted keys are stable`() {
        assertEquals(listOf("protected", "sensitive", "normal"), PrivacyTier.entries.map { it.key })
        assertEquals(
            listOf("app_control", "drop_password_fields", "preview_before_send", "redact_patterns", "audit_log", "propose_by_name"),
            SecurityMeasure.entries.map { it.key },
        )
    }

    @Test
    fun `an untouched measure is enabled and an explicit choice wins`() {
        val s = PrivacySettings(measures = mapOf(SecurityMeasure.AUDIT_LOG to false))

        assertFalse(s.isEnabled(SecurityMeasure.AUDIT_LOG))
        assertTrue(s.isEnabled(SecurityMeasure.REDACT_PATTERNS))
    }

    @Test
    fun `an exact match only matches that package`() {
        assertTrue(AppMatch.Exact("a.b").matches("a.b"))
        assertFalse(AppMatch.Exact("a.b").matches("a.b.c"))
    }

    @Test
    fun `a prefix matches at the package boundary, never in the middle of a name`() {
        val m = AppMatch.Prefix("com.bbva")

        assertTrue(m.matches("com.bbva"))
        assertTrue(m.matches("com.bbva.app"))
        assertFalse(m.matches("com.bbvax"))
        assertFalse(m.matches("org.com.bbva"))
    }

    @Test
    fun `rules round-trip through their persisted form`() {
        val rules = listOf(
            UserRule(AppMatch.Exact("com.mi.banco"), PrivacyTier.PROTECTED),
            UserRule(AppMatch.Prefix("com.acme"), PrivacyTier.NORMAL),
        )

        rules.forEach { assertEquals(it, UserRule.decode(it.encode())) }
    }

    @Test
    fun `corrupt or unknown persisted rules decode to null instead of throwing`() {
        listOf("", "garbage", "protected|", "protected|nope:x", "weird|exact:a.b", "protected|exact: ").forEach {
            assertNull(it, UserRule.decode(it))
        }
    }

    @Test
    fun `default regions are global plus the device country`() {
        assertEquals(setOf("GLOBAL", "ES"), PrivacyRegions.defaultFor("es"))
        assertEquals(setOf("GLOBAL"), PrivacyRegions.defaultFor(null))
        assertEquals(setOf("GLOBAL"), PrivacyRegions.defaultFor("ESP")) // no es un codigo de 2 letras
    }
}
