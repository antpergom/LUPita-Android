package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    @Test
    fun `a small image is never enlarged`() {
        assertEquals(ImageSizing.Size(300, 200), ImageSizing.fit(300, 200))
    }

    @Test
    fun `an image exactly at the cap is left alone`() {
        assertEquals(ImageSizing.Size(1300, 1300), ImageSizing.fit(1300, 1300))
    }

    @Test
    fun `a big square shrinks to the cap`() {
        val s = ImageSizing.fit(2600, 2600)
        assertEquals(1300, s.width)
        assertEquals(1300, s.height)
    }

    @Test
    fun `a full portrait screen keeps its proportions and stays under the cap`() {
        val s = ImageSizing.fit(1440, 3120)
        assertTrue(s.width.toLong() * s.height <= ImageSizing.MAX_AREA_PX)
        // Mas legible que un tope por lado largo (600 x 1300).
        assertTrue(s.height > 1300)
        assertEquals(1440.0 / 3120.0, s.width.toDouble() / s.height, 0.01)
    }

    @Test
    fun `an extreme ratio never collapses to zero`() {
        val s = ImageSizing.fit(4, 4_000_000)
        assertTrue(s.width >= 1 && s.height >= 1)
    }

    @Test
    fun `an empty image is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ImageSizing.fit(0, 10) }
    }
}
