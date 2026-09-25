package com.antoniopg.lupita.capability.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGateTest {
    @Test
    fun `empty text should run ocr`() {
        assertTrue(OcrGate.shouldRun(""))
    }

    @Test
    fun `whitespace-only text should run ocr, trimmed length is what counts`() {
        assertTrue(OcrGate.shouldRun("   \n\n   "))
    }

    @Test
    fun `a short fragment below the threshold should run ocr`() {
        assertTrue(OcrGate.shouldRun("solo esto"))
    }

    @Test
    fun `substantial text should not run ocr`() {
        val text = "Este es un texto normalizado con contenido real de sobra para analizar sin OCR."
        assertFalse(OcrGate.shouldRun(text))
    }
}
