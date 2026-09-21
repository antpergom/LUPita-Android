package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {

    @Test
    fun `spanish and english are supported and named in their own language`() {
        assertEquals(listOf("es", "en"), Languages.supported.map { it.tag })
        assertEquals(listOf("Español", "English"), Languages.supported.map { it.displayName })
    }

    @Test
    fun `the region is ignored - only the language counts`() {
        assertEquals("es", Languages.resolve(appTag = null, systemTag = "es-ES"))
        assertEquals("en", Languages.resolve(appTag = null, systemTag = "en-US"))
        assertEquals("en", Languages.resolve(appTag = null, systemTag = "en_GB"))
    }

    @Test
    fun `the language chosen in the app beats the system one`() {
        assertEquals("en", Languages.resolve(appTag = "en", systemTag = "es-ES"))
        assertEquals("es", Languages.resolve(appTag = "es-MX", systemTag = "en-US"))
    }

    @Test
    fun `an unsupported app language falls through to the system language`() {
        assertEquals("en", Languages.resolve(appTag = "fr", systemTag = "en-US"))
    }

    @Test
    fun `when nothing is supported the fallback is used`() {
        assertEquals(Languages.FALLBACK, Languages.resolve(appTag = "fr", systemTag = "de-DE"))
        assertEquals(Languages.FALLBACK, Languages.resolve(appTag = null, systemTag = null))
    }

    @Test
    fun `the fallback is itself a supported language`() {
        assertTrue(Languages.supported.any { it.tag == Languages.FALLBACK })
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals("en", Languages.resolve(appTag = "EN-us", systemTag = null))
    }
}
