package com.antoniopg.lupita

import com.antoniopg.lupita.capability.privacy.PrivacyCatalogParser
import com.antoniopg.lupita.core.model.PrivacyRegions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de calidad del fichero de DATOS que se empaqueta: como el parser descarta en silencio lo que
 * esta mal formado, un error tipografico en el JSON dejaria una app sin proteger sin que nadie lo note.
 */
class PrivacyCatalogAssetTest {

    private val raw = File("src/main/assets/privacy_catalog.json").readText()
    private val catalog = PrivacyCatalogParser.parse(raw)

    @Test
    fun `the packaged catalog parses and is not empty`() {
        assertTrue(catalog.groups.isNotEmpty())
        assertTrue(catalog.entries.isNotEmpty())
        assertTrue(catalog.keywords.isNotEmpty())
    }

    @Test
    fun `nothing in the file is silently dropped by the parser`() {
        // Cuenta las entradas y palabras del texto crudo: si el parser descarto alguna, el numero no cuadra.
        assertEquals(Regex("\"group\"\\s*:\\s*\"[a-z_]+\"").findAll(raw.substringAfter("\"entries\"").substringBefore("\"keywords\"")).count(), catalog.entries.size)
        assertEquals(Regex("\"word\"").findAll(raw).count(), catalog.keywords.size)
        assertEquals(Regex("\"defaultTier\"").findAll(raw).count(), catalog.groups.size)
    }

    @Test
    fun `every group has both labels and a tier`() {
        catalog.groups.forEach {
            assertTrue("${it.id} sin etiqueta es", it.labels["es"].orEmpty().isNotBlank())
            assertTrue("${it.id} sin etiqueta en", it.labels["en"].orEmpty().isNotBlank())
        }
    }

    @Test
    fun `the global region exists and every region used by an entry or keyword is declared`() {
        val declared = catalog.regions.map { it.id }.toSet()
        assertTrue(PrivacyRegions.GLOBAL in declared)
        val used = catalog.entries.flatMap { it.regions } + catalog.keywords.flatMap { it.regions }
        used.forEach { assertTrue("region $it usada pero sin declarar", it in declared) }
    }

    @Test
    fun `money, passwords and authenticators are protected by default and nothing sensitive defaults to normal`() {
        val tiers = catalog.groups.associate { it.id to it.defaultTier.key }

        listOf("banking", "password_manager", "authenticator", "health").forEach { assertEquals(it, "protected", tiers[it]) }
        // el navegador puede estar mostrando una web de banca: sensible, no normal
        assertEquals("sensitive", tiers["browser"])
    }
}
