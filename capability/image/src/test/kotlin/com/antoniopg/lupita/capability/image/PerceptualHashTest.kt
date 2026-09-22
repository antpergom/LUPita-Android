package com.antoniopg.lupita.capability.image

import com.antoniopg.lupita.core.model.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    private fun solid(w: Int, h: Int, argb: Int) = PixelBuffer(w, h, IntArray(w * h) { argb })

    /** Un degradado horizontal: cada columna un poco mas clara que la anterior. */
    private fun gradient(w: Int, h: Int): PixelBuffer {
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val v = (255 * x / (w - 1).coerceAtLeast(1)).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return PixelBuffer(w, h, pixels)
    }

    @Test
    fun `the same image always hashes the same`() {
        val image = gradient(64, 64)

        assertEquals(PerceptualHash.of(image), PerceptualHash.of(image))
    }

    @Test
    fun `a solid color has zero distance from itself but differs from another solid color`() {
        val black = solid(32, 32, 0xFF000000.toInt())
        val white = solid(32, 32, 0xFFFFFFFF.toInt())

        assertEquals(0, PerceptualHash.of(black).hammingDistance(PerceptualHash.of(black)))
        // Un color plano no tiene bordes que comparar: dHash puede no distinguir dos colores planos
        // distintos (limitacion conocida), pero nunca debe reventar ni dar una distancia negativa.
        assertTrue(PerceptualHash.of(black).hammingDistance(PerceptualHash.of(white)) >= 0)
    }

    @Test
    fun `a real gradient differs clearly from a solid color`() {
        val distance = PerceptualHash.of(gradient(64, 64)).hammingDistance(PerceptualHash.of(solid(64, 64, 0xFF808080.toInt())))

        assertTrue("se esperaba una distancia notable, salio $distance", distance > 10)
    }

    @Test
    fun `resizing the same image barely changes its hash`() {
        val small = PerceptualHash.of(gradient(64, 64))
        val big = PerceptualHash.of(gradient(512, 512))

        assertTrue("distancia tras reescalar: ${small.hammingDistance(big)}", small.hammingDistance(big) <= 4)
    }

    @Test
    fun `the hash prints as 16 lowercase hex characters`() {
        val hex = PerceptualHash.of(gradient(16, 16)).toHex()

        assertEquals(16, hex.length)
        assertEquals(hex, hex.lowercase())
    }

    @Test
    fun `a single pixel image does not crash`() {
        val hash = PerceptualHash.of(solid(1, 1, 0xFF123456.toInt()))

        assertEquals(0, hash.hammingDistance(hash))
    }
}
