package com.antoniopg.lupita.ui.overlay

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.antoniopg.lupita.core.model.SelectionRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLayoutTest {

    // Los numeros del Pixel 6 Pro donde se vio el fallo: 1440 px de ancho y densidad 3,5.
    private val w = 1440
    private val d = 3.5f
    private val selection = SelectionRect(300, 800, 950, 1520)

    private fun center(r: IntRect) = IntOffset((r.left + r.right) / 2, (r.top + r.bottom) / 2)

    @Test
    fun `the action buttons sit above the rectangle`() {
        val r = CaptureLayout.actionButtons(selection, w, d)

        assertEquals(IntRect(300, 625, 615, 765), r)
    }

    @Test
    fun `the action buttons stick to the top margin when the rectangle is near the top`() {
        val r = CaptureLayout.actionButtons(SelectionRect(300, 60, 900, 400), w, d)

        assertEquals(28, r.top)
    }

    @Test
    fun `the action buttons never leave the screen on the right`() {
        val r = CaptureLayout.actionButtons(SelectionRect(1400, 800, 1440, 900), w, d)

        assertTrue(r.right <= w)
        assertEquals(w - 336, r.left)
    }

    @Test
    fun `a touch on the center of either action button does not start a stroke`() {
        val buttons = CaptureLayout.actionButtons(selection, w, d)
        val cancel = IntOffset(buttons.left + 70, center(buttons).y)
        val confirm = IntOffset(buttons.left + 245, center(buttons).y)

        assertTrue(CaptureLayout.startsOnControl(cancel, selection, w, d))
        assertTrue(CaptureLayout.startsOnControl(confirm, selection, w, d))
    }

    @Test
    fun `a touch on the close button does not start a stroke - the case that made a new button appear there`() {
        assertTrue(CaptureLayout.startsOnControl(center(CaptureLayout.closeButton(w, d)), selection, w, d))
        assertTrue(CaptureLayout.startsOnControl(center(CaptureLayout.closeButton(w, d)), null, w, d))
    }

    @Test
    fun `a finger that lands just beside a button still counts as the button`() {
        val buttons = CaptureLayout.actionButtons(selection, w, d)
        val justOutside = IntOffset(buttons.right + 20, center(buttons).y) // 20 px = ~6 dp, dentro del margen

        assertTrue(CaptureLayout.startsOnControl(justOutside, selection, w, d))
    }

    @Test
    fun `far from every control a touch does start a stroke`() {
        assertFalse(CaptureLayout.startsOnControl(IntOffset(700, 1200), selection, w, d)) // dentro del rectangulo
        assertFalse(CaptureLayout.startsOnControl(IntOffset(100, 2500), selection, w, d))
        assertFalse(CaptureLayout.startsOnControl(IntOffset(700, 1200), null, w, d))
    }

    @Test
    fun `before there is a selection the action buttons do not exist, so their spot is drawable`() {
        val whereTheyWouldBe = IntOffset(370, 695)

        assertFalse(CaptureLayout.startsOnControl(whereTheyWouldBe, null, w, d))
        assertTrue(CaptureLayout.startsOnControl(whereTheyWouldBe, selection, w, d))
    }

    @Test
    fun `the exclusion zone is exactly the button inflated by the margin`() {
        val buttons = CaptureLayout.actionButtons(selection, w, d)
        val margin = (CaptureLayout.CONTROL_MARGIN_DP * d).toInt()
        val zone = CaptureLayout.controlZones(selection, w, d).last()

        assertEquals(buttons.inflate(margin), zone)
    }
}
