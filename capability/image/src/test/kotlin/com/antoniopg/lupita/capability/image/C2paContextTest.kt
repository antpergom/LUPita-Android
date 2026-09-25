package com.antoniopg.lupita.capability.image

import org.junit.Assert.assertTrue
import org.junit.Test

class C2paContextTest {
    @Test
    fun `presence is reported, with the label when there is one`() {
        val block = C2paDetection(present = true, label = "adobe:manifest").asPromptContext()

        assertTrue(block.contains("SI contiene"))
        assertTrue(block.contains("adobe:manifest"))
    }

    @Test
    fun `presence without a label still says so, without a stray label mention`() {
        val block = C2paDetection(present = true, label = null).asPromptContext()

        assertTrue(block.contains("SI contiene"))
        assertTrue(!block.contains("etiqueta:"))
    }

    @Test
    fun `absence is reported as information too, never as a silent empty string`() {
        val block = C2paDetection(present = false).asPromptContext()

        assertTrue(block.contains("NO contiene"))
        assertTrue(block.isNotBlank())
    }

    @Test
    fun `neither presence nor absence is framed as conclusive proof`() {
        assertTrue(C2paDetection(present = true).asPromptContext().contains("no prueba"))
        assertTrue(C2paDetection(present = false).asPromptContext().contains("tampoco prueba"))
    }
}
