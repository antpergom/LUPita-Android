package com.antoniopg.lupita.capability.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityContextTest {
    @Test
    fun `empty entities give no context block`() {
        assertNull(ExtractedEntities().asPromptContext())
    }

    @Test
    fun `only mentions still produce a block, with the at sign restored`() {
        val block = ExtractedEntities(mentions = listOf("rubiu5", "otra")).asPromptContext()!!

        assertTrue(block.contains("@rubiu5, @otra"))
        assertTrue(block.contains("Menciones:"))
    }

    @Test
    fun `only hashtags restore the hash sign`() {
        val block = ExtractedEntities(hashtags = listOf("noticia")).asPromptContext()!!

        assertTrue(block.contains("#noticia"))
    }

    @Test
    fun `a mix of all four kinds lists each on its own line`() {
        val block = ExtractedEntities(
            urls = listOf("https://example.com"),
            mentions = listOf("a"),
            hashtags = listOf("b"),
            emails = listOf("c@d.com"),
        ).asPromptContext()!!

        val lines = block.lines()
        assertEquals(1, lines.count { it.startsWith("- Menciones:") })
        assertEquals(1, lines.count { it.startsWith("- Hashtags:") })
        assertEquals(1, lines.count { it.startsWith("- URLs:") })
        assertEquals(1, lines.count { it.startsWith("- Correos:") })
    }

    @Test
    fun `the leading disclaimer always says the extraction is deterministic, not from the model`() {
        val block = ExtractedEntities(emails = listOf("a@b.com")).asPromptContext()!!

        assertTrue(block.contains("determinista"))
    }
}
