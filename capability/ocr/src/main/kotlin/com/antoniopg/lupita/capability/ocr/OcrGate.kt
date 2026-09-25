package com.antoniopg.lupita.capability.ocr

/**
 * Decision PURA de cuando merece la pena pagar la latencia (y la posible falta de Play Services) de
 * ML Kit (2026-09-25, cablea F3 al flujo real): solo si el texto ya extraido del arbol de
 * accesibilidad es demasiado corto para servir de contexto por si solo — memes, capturas de imagen
 * pura, apps sin arbol legible. Con texto ya sustancioso, el OCR no aportaria nada nuevo y solo
 * anadiria tiempo de espera. Separada de `TextOcr.recognize()` (que SI toca ML Kit y no se puede
 * probar en una JVM de escritorio) precisamente para que esta decision si tenga test.
 */
object OcrGate {
    private const val MIN_USABLE_CHARS = 40

    fun shouldRun(normalizedText: String): Boolean = normalizedText.trim().length < MIN_USABLE_CHARS
}
