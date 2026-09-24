package com.antoniopg.lupita.ui.overlay

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsPanelTest {
    @Test
    fun `plain text without any bold marker is untouched`() {
        val result = boldMarkdown("texto normal sin nada")

        assertEquals("texto normal sin nada", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `one bold span becomes real bold, without the asterisks`() {
        val result = boldMarkdown("es una publicacion de **@yonki_mercados**, verificada")

        assertEquals("es una publicacion de @yonki_mercados, verificada", result.text)
        val span = result.spanStyles.single()
        assertEquals(SpanStyle(fontWeight = FontWeight.Bold), span.item)
        assertEquals("@yonki_mercados", result.text.substring(span.start, span.end))
    }

    @Test
    fun `several bold spans in the same text all get bold`() {
        val result = boldMarkdown("**uno** y **dos** y **tres**")

        assertEquals("uno y dos y tres", result.text)
        assertEquals(3, result.spanStyles.size)
    }

    @Test
    fun `an unclosed marker is kept literally, not silently dropped`() {
        val result = boldMarkdown("esto tiene un ** suelto sin cerrar")

        assertEquals("esto tiene un ** suelto sin cerrar", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", boldMarkdown("").text)
    }
}
