package com.antoniopg.lupita.ui.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissZoneTest {

    private val w = 1440
    private val h = 3120
    private val density = 3.5f
    private val cx = DismissZone.centerX(w)
    private val cy = DismissZone.centerY(h, density)

    private fun hit(x: Float, y: Float) = DismissZone.contains(x, y, w, h, density)

    @Test
    fun `the center of the target is a hit`() {
        assertTrue(hit(cx, cy))
    }

    @Test
    fun `just inside the hit radius is a hit and just outside is not`() {
        val r = DismissZone.HIT_RADIUS_DP * density
        assertTrue(hit(cx + r - 1, cy))
        assertFalse(hit(cx + r + 1, cy))
    }

    @Test
    fun `the middle of the screen and the usual bubble spots are not hits`() {
        assertFalse(hit(w / 2f, h / 2f))
        assertFalse(hit(w - 100f, h / 3f))
    }

    @Test
    fun `the target sits above the bottom edge`() {
        assertTrue(cy < h)
        assertTrue(cy > h / 2f)
    }
}
