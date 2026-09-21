package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.AppSignals
import com.antoniopg.lupita.core.model.CatalogEntry
import com.antoniopg.lupita.core.model.DecisionSource
import com.antoniopg.lupita.core.model.KeywordRule
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyGroup
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.UserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyGateTest {

    private val catalog = PrivacyCatalog(
        groups = listOf(
            PrivacyGroup("banking", emptyMap(), PrivacyTier.PROTECTED),
            PrivacyGroup("social", emptyMap(), PrivacyTier.NORMAL),
            PrivacyGroup("messaging", emptyMap(), PrivacyTier.SENSITIVE),
        ),
        entries = listOf(
            CatalogEntry(AppMatch.Exact("com.revolut.revolut"), "banking", setOf("GLOBAL")),
            CatalogEntry(AppMatch.Prefix("com.bbva"), "banking", setOf("ES")),
            CatalogEntry(AppMatch.Exact("com.bbva.fun"), "social", setOf("ES")), // mas especifica que el prefijo
            CatalogEntry(AppMatch.Exact("com.instagram.android"), "social", setOf("GLOBAL")),
            CatalogEntry(AppMatch.Exact("com.whatsapp"), "messaging", setOf("GLOBAL")),
        ),
        keywords = listOf(
            KeywordRule("bank", "banking", setOf("GLOBAL")),
            KeywordRule("banco", "banking", setOf("ES")),
        ),
    )
    private val gate = PrivacyGate(catalog)
    private val plain = AppSignals()

    private fun settings(
        unknown: PrivacyTier = PrivacyTier.SENSITIVE,
        regions: Set<String> = setOf("GLOBAL", "ES"),
        groupTiers: Map<String, PrivacyTier> = emptyMap(),
        rules: List<UserRule> = emptyList(),
        measures: Map<SecurityMeasure, Boolean> = emptyMap(),
    ) = PrivacySettings(measures, unknown, groupTiers, regions, rules)

    private fun decide(pkg: String, s: PrivacySettings = settings(), signals: AppSignals = plain) = gate.decide(pkg, signals, s)

    // --- por defecto ---
    @Test
    fun `an unknown app is SENSITIVE by default - never normal`() {
        val d = decide("com.example.random")

        assertEquals(PrivacyTier.SENSITIVE, d.tier)
        assertEquals(DecisionSource.UNKNOWN_DEFAULT, d.source)
    }

    @Test
    fun `the factory settings treat unknown apps as sensitive and every measure as enabled`() {
        val factory = PrivacySettings()

        assertEquals(PrivacyTier.SENSITIVE, factory.unknownAppTier)
        SecurityMeasure.entries.forEach { assertEquals(true, factory.isEnabled(it)) }
    }

    @Test
    fun `the unknown-app level is configurable`() {
        assertEquals(PrivacyTier.PROTECTED, decide("com.example.random", settings(unknown = PrivacyTier.PROTECTED)).tier)
        assertEquals(PrivacyTier.NORMAL, decide("com.example.random", settings(unknown = PrivacyTier.NORMAL)).tier)
    }

    // --- catalogo ---
    @Test
    fun `a known bank is protected by the catalog`() {
        val d = decide("com.revolut.revolut")

        assertEquals(PrivacyTier.PROTECTED, d.tier)
        assertEquals(DecisionSource.CATALOG, d.source)
        assertEquals("banking", d.group)
    }

    @Test
    fun `a prefix rule covers every app of the brand, at the package boundary only`() {
        assertEquals(PrivacyTier.PROTECTED, decide("com.bbva.bbvacontigo").tier)
        assertEquals(DecisionSource.UNKNOWN_DEFAULT, decide("com.bbvax.other").source)
    }

    @Test
    fun `an exact catalog entry beats a prefix`() {
        assertEquals(PrivacyTier.NORMAL, decide("com.bbva.fun").tier)
    }

    // --- regiones ---
    @Test
    fun `entries of a region that is switched off do not apply`() {
        val withoutSpain = settings(regions = setOf("GLOBAL"))

        assertEquals(DecisionSource.UNKNOWN_DEFAULT, decide("com.bbva.bbvacontigo", withoutSpain).source)
        assertEquals(PrivacyTier.PROTECTED, decide("com.revolut.revolut", withoutSpain).tier)
    }

    // --- grupos ---
    @Test
    fun `the user can change the level of a whole group`() {
        val s = settings(groupTiers = mapOf("social" to PrivacyTier.SENSITIVE))

        assertEquals(PrivacyTier.SENSITIVE, decide("com.instagram.android", s).tier)
        assertEquals(PrivacyTier.PROTECTED, decide("com.revolut.revolut", s).tier) // otros grupos, intactos
    }

    // --- reglas del usuario ---
    @Test
    fun `a user rule beats the catalog`() {
        val s = settings(rules = listOf(UserRule(AppMatch.Exact("com.revolut.revolut"), PrivacyTier.NORMAL)))

        val d = decide("com.revolut.revolut", s)

        assertEquals(PrivacyTier.NORMAL, d.tier)
        assertEquals(DecisionSource.USER_RULE, d.source)
    }

    @Test
    fun `a user rule can protect an app the catalog does not know`() {
        val s = settings(rules = listOf(UserRule(AppMatch.Exact("com.mi.banco.local"), PrivacyTier.PROTECTED)))

        assertEquals(PrivacyTier.PROTECTED, decide("com.mi.banco.local", s).tier)
    }

    @Test
    fun `among user rules the exact one wins, then the longest prefix`() {
        val s = settings(
            rules = listOf(
                UserRule(AppMatch.Prefix("com.acme"), PrivacyTier.PROTECTED),
                UserRule(AppMatch.Prefix("com.acme.tools"), PrivacyTier.NORMAL),
                UserRule(AppMatch.Exact("com.acme.tools.vault"), PrivacyTier.PROTECTED),
            ),
        )

        assertEquals(PrivacyTier.PROTECTED, decide("com.acme.other", s).tier)
        assertEquals(PrivacyTier.NORMAL, decide("com.acme.tools.editor", s).tier)
        assertEquals(PrivacyTier.PROTECTED, decide("com.acme.tools.vault", s).tier)
    }

    // --- ventana protegida por el sistema ---
    @Test
    fun `a secure window is always protected, whatever the user or the catalog say`() {
        val s = settings(rules = listOf(UserRule(AppMatch.Exact("com.instagram.android"), PrivacyTier.NORMAL)))

        val d = decide("com.instagram.android", s, AppSignals(secureWindow = true))

        assertEquals(PrivacyTier.PROTECTED, d.tier)
        assertEquals(DecisionSource.SECURE_WINDOW, d.source)
    }

    @Test
    fun `a secure window stays protected even with app control switched off`() {
        val off = settings(measures = mapOf(SecurityMeasure.APP_CONTROL to false))

        assertEquals(PrivacyTier.PROTECTED, decide("com.example.x", off, AppSignals(secureWindow = true)).tier)
    }

    // --- medida desactivada ---
    @Test
    fun `with app control switched off everything is normal`() {
        val off = settings(measures = mapOf(SecurityMeasure.APP_CONTROL to false))

        val d = decide("com.revolut.revolut", off)

        assertEquals(PrivacyTier.NORMAL, d.tier)
        assertEquals(DecisionSource.CONTROL_DISABLED, d.source)
    }

    // --- propuestas por nombre ---
    @Test
    fun `an unknown app whose name looks financial gets a PROPOSAL, not a decision`() {
        val d = decide("com.mycompany.bankapp")

        assertEquals(PrivacyTier.SENSITIVE, d.tier) // la decision sigue siendo la de desconocidas
        assertEquals("banking", d.suggestion?.group)
        assertEquals(PrivacyTier.PROTECTED, d.suggestion?.tier)
    }

    @Test
    fun `name proposals respect the region and can be switched off`() {
        assertNull(decide("es.mi.banco", settings(regions = setOf("GLOBAL"))).suggestion) // 'banco' es de ES
        assertEquals("banking", decide("es.mi.banco", settings(regions = setOf("GLOBAL", "ES"))).suggestion?.group)

        val off = settings(measures = mapOf(SecurityMeasure.PROPOSE_BY_NAME to false))
        assertNull(decide("com.mycompany.bankapp", off).suggestion)
    }

    @Test
    fun `a known app never carries a name proposal`() {
        assertNull(decide("com.revolut.revolut").suggestion)
    }

    @Test
    fun `an empty catalog makes every app unknown - the safe side`() {
        val d = PrivacyGate(PrivacyCatalog()).decide("com.revolut.revolut", plain, PrivacySettings())

        assertEquals(PrivacyTier.SENSITIVE, d.tier)
    }
}
