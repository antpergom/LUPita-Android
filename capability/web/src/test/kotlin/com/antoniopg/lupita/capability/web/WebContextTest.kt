package com.antoniopg.lupita.capability.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContextTest {
    @Test
    fun `blank main text gives no context block, no fake source`() {
        val content = ReadableContent(WebMetadata(), "   ")

        assertNull(content.asPromptContext("https://example.com"))
    }

    @Test
    fun `the url and the real body are both present`() {
        val content = ReadableContent(WebMetadata(), "El cuerpo real del articulo.")

        val block = content.asPromptContext("https://example.com/noticia")!!

        assertTrue(block.contains("https://example.com/noticia"))
        assertTrue(block.contains("El cuerpo real del articulo."))
    }

    @Test
    fun `the title is included when known, omitted when not`() {
        val withTitle = ReadableContent(WebMetadata(title = "Un titulo"), "cuerpo").asPromptContext("u")!!
        val withoutTitle = ReadableContent(WebMetadata(), "cuerpo").asPromptContext("u")!!

        assertTrue(withTitle.contains("Titulo: Un titulo"))
        assertTrue(!withoutTitle.contains("Titulo:"))
    }

    @Test
    fun `very long articles are truncated to a bounded size`() {
        // 'z' no aparece en ningun otro texto fijo del bloque (cabecera/titulo/url), para que contar
        // sus apariciones mida solo lo que sobrevive del cuerpo tras el recorte.
        val huge = "z".repeat(5_000)
        val content = ReadableContent(WebMetadata(), huge)

        val block = content.asPromptContext("u")!!

        assertEquals(2_000, block.count { it == 'z' })
    }
}
