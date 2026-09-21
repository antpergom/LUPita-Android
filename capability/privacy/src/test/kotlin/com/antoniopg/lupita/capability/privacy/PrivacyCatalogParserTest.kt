package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.localized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyCatalogParserTest {

    private val valid = """
        {
          "groups": [
            {"id":"banking","label":{"es":"Banca","en":"Banking"},"defaultTier":"protected"},
            {"id":"social","label":{"es":"Redes"},"defaultTier":"normal"}
          ],
          "regions": [{"id":"GLOBAL","label":{"es":"Global"}}, {"id":"ES","label":{"es":"España","en":"Spain"}}],
          "entries": [
            {"package":"com.revolut.revolut","group":"banking"},
            {"prefix":"com.bbva.","group":"banking","regions":["es"]}
          ],
          "keywords": [{"word":"Bank","group":"banking"}]
        }
    """.trimIndent()

    @Test
    fun `a valid catalog is parsed with groups, regions, entries and keywords`() {
        val c = PrivacyCatalogParser.parse(valid)

        assertEquals(listOf("banking", "social"), c.groups.map { it.id })
        assertEquals(PrivacyTier.PROTECTED, c.group("banking")?.defaultTier)
        assertEquals(listOf("GLOBAL", "ES"), c.regions.map { it.id })
        assertEquals(2, c.entries.size)
        assertEquals(1, c.keywords.size)
    }

    @Test
    fun `entries default to the global region and regions are normalised to upper case`() {
        val c = PrivacyCatalogParser.parse(valid)

        assertEquals(setOf("GLOBAL"), c.entries[0].regions)
        assertEquals(setOf("ES"), c.entries[1].regions)
    }

    @Test
    fun `a prefix is stored without its trailing dot and keywords are lower-cased`() {
        val c = PrivacyCatalogParser.parse(valid)

        assertEquals(AppMatch.Prefix("com.bbva"), c.entries[1].match)
        assertEquals("bank", c.keywords[0].word)
    }

    @Test
    fun `malformed json gives an empty catalog instead of crashing`() {
        assertEquals(0, PrivacyCatalogParser.parse("{nope").groups.size)
        assertEquals(0, PrivacyCatalogParser.parse("").entries.size)
    }

    @Test
    fun `an unreadable tier falls back to SENSITIVE - it must never relax protection`() {
        val c = PrivacyCatalogParser.parse("""{"groups":[{"id":"x","defaultTier":"whatever"}]}""")

        assertEquals(PrivacyTier.SENSITIVE, c.group("x")?.defaultTier)
    }

    @Test
    fun `entries pointing to an unknown group or without a package or prefix are dropped, the rest survive`() {
        val c = PrivacyCatalogParser.parse(
            """
            {"groups":[{"id":"g"}],
             "entries":[{"package":"a.b","group":"g"},{"package":"c.d","group":"ghost"},{"group":"g"},{"package":" ","group":"g"}]}
            """.trimIndent(),
        )

        assertEquals(listOf(AppMatch.Exact("a.b")), c.entries.map { it.match })
    }

    @Test
    fun `unknown keys are ignored so the file can grow`() {
        val c = PrivacyCatalogParser.parse("""{"version":3,"groups":[{"id":"g","color":"red"}]}""")

        assertEquals(1, c.groups.size)
    }

    @Test
    fun `duplicated group ids keep the first one`() {
        val c = PrivacyCatalogParser.parse("""{"groups":[{"id":"g","defaultTier":"protected"},{"id":"g","defaultTier":"normal"}]}""")

        assertEquals(PrivacyTier.PROTECTED, c.group("g")?.defaultTier)
    }

    @Test
    fun `labels are picked by language with a spanish fallback`() {
        val labels = PrivacyCatalogParser.parse(valid).regions[1].labels

        assertEquals("Spain", labels.localized("en", "ES"))
        assertEquals("España", labels.localized("fr", "ES"))
        assertTrue(emptyMap<String, String>().localized("en", "fallback") == "fallback")
    }
}
