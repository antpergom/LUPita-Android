package com.antoniopg.lupita.capability.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityExtractorTest {

    @Test
    fun `urls are found and stop at whitespace or closing punctuation`() {
        val text = "Mira esto: https://example.com/foo?a=1 (visto en https://x.com)."

        val result = EntityExtractor.extract(text)

        assertEquals(listOf("https://example.com/foo?a=1", "https://x.com"), result.urls)
    }

    @Test
    fun `mentions and hashtags are extracted without their marker`() {
        val text = "Gracias @ana_dev por el #kotlin de hoy, cc @Pedro99 #AndroidDev"

        val result = EntityExtractor.extract(text)

        assertEquals(listOf("ana_dev", "Pedro99"), result.mentions)
        assertEquals(listOf("kotlin", "AndroidDev"), result.hashtags)
    }

    @Test
    fun `an email is not misread as part of a mention`() {
        val result = EntityExtractor.extract("Escribeme a ana@example.com si quieres.")

        assertEquals(listOf("ana@example.com"), result.emails)
        assertTrue(result.mentions.isEmpty())
    }

    @Test
    fun `duplicates are collapsed but order is kept`() {
        val result = EntityExtractor.extract("#kotlin es genial, #kotlin de verdad")

        assertEquals(listOf("kotlin"), result.hashtags)
    }

    @Test
    fun `plain text with none of this comes back empty`() {
        val result = EntityExtractor.extract("Hola, que tal estas hoy.")

        assertTrue(result.isEmpty)
    }

    @Test
    fun `a hashtag keeps accented letters`() {
        assertEquals(listOf("aquí"), EntityExtractor.extract("mira #aquí").hashtags)
    }
}
