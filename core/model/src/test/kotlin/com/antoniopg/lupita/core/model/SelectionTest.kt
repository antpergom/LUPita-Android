package com.antoniopg.lupita.core.model

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    // Pantalla de 1000 x 2000 y minimo de 200 px, para que las cuentas se vean a ojo.
    private val w = 1000
    private val h = 2000
    private val min = 200

    private fun stroke(vararg pts: Pair<Float, Float>) =
        StrokeRecorder().also { r -> pts.forEach { r.add(it.first, it.second) } }

    private fun select(r: StrokeRecorder) = Selection.fromStroke(r, min, w, h)

    private fun centerX(r: SelectionRect) = (r.left + r.right) / 2
    private fun centerY(r: SelectionRect) = (r.top + r.bottom) / 2

    @Test
    fun `the rectangle is exactly the bounding box of a normal stroke`() {
        val rect = select(stroke(300f to 800f, 900f to 820f, 950f to 1500f, 320f to 1520f))

        assertEquals(SelectionRect(300, 800, 950, 1520), rect)
    }

    @Test
    fun `a tiny stroke grows to the minimum keeping its center`() {
        val rect = select(stroke(500f to 1000f, 510f to 1004f))!!

        assertEquals(min, rect.width)
        assertEquals(min, rect.height)
        assertTrue(abs(centerX(rect) - 505) <= 1)
        assertTrue(abs(centerY(rect) - 1002) <= 1)
    }

    @Test
    fun `only the short axis grows - a thin horizontal line keeps its length`() {
        val rect = select(stroke(200f to 1000f, 800f to 1000f))!!

        assertEquals(600, rect.width)
        assertEquals(min, rect.height)
        assertTrue(abs(centerY(rect) - 1000) <= 1)
    }

    @Test
    fun `two identical points still count as a stroke and grow around that point`() {
        val rect = select(stroke(400f to 900f, 400f to 900f))!!

        assertEquals(min, rect.width)
        assertEquals(min, rect.height)
        assertTrue(abs(centerX(rect) - 400) <= 1)
        assertTrue(abs(centerY(rect) - 900) <= 1)
    }

    @Test
    fun `near the left and top edges the rectangle shifts inward without changing size`() {
        val rect = select(stroke(0f to 0f, 10f to 6f))!!

        assertEquals(SelectionRect(0, 0, min, min), rect)
    }

    @Test
    fun `near the right and bottom edges the rectangle shifts inward without changing size`() {
        val rect = select(stroke(990f to 1994f, 999f to 1999f))!!

        assertEquals(SelectionRect(w - min, h - min, w, h), rect)
    }

    @Test
    fun `after shifting inward the rectangle still contains the whole stroke`() {
        val rect = select(stroke(2f to 1500f, 12f to 1510f))!!

        assertTrue(rect.left <= 2 && rect.right >= 12 && rect.top <= 1500 && rect.bottom >= 1510)
        assertTrue(rect.left >= 0 && rect.right <= w)
    }

    @Test
    fun `a single point is not a selection`() {
        assertNull(select(stroke(500f to 500f)))
    }

    @Test
    fun `an empty stroke is not a selection`() {
        assertNull(select(StrokeRecorder()))
    }

    @Test
    fun `points outside the screen are clamped to it`() {
        val rect = select(stroke(-50f to -20f, 400f to 900f))!!

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertTrue(rect.right <= w && rect.bottom <= h)
    }

    @Test
    fun `a minimum bigger than the screen is limited to the screen size`() {
        val rect = Selection.fromStroke(stroke(10f to 10f, 20f to 20f), minSizePx = 5000, screenWidth = w, screenHeight = h)!!

        assertEquals(SelectionRect(0, 0, w, h), rect)
    }

    @Test
    fun `the bounding box uses ALL the points even when only the last ones are kept for drawing`() {
        val recorder = StrokeRecorder(maxKeptPoints = 3)
        recorder.add(100f, 100f) // el primero: se acabara descartando
        (1..9).forEach { recorder.add(500f + it, 600f + it) }

        assertEquals(3, recorder.points.size)
        assertEquals(TouchPoint(509f, 609f), recorder.points.last())
        assertEquals(StrokeBounds(100f, 100f, 509f, 609f), recorder.bounds())
        // y el rectangulo sale de la caja completa, no de los ultimos 3 puntos
        assertEquals(SelectionRect(100, 100, 509, 609), select(recorder))
    }

    @Test
    fun `reset forgets the stroke`() {
        val recorder = stroke(100f to 100f, 300f to 300f)
        recorder.reset()

        assertEquals(0, recorder.count)
        assertNull(recorder.bounds())
        assertTrue(recorder.points.isEmpty())
    }

    @Test
    fun `an inverted rectangle is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SelectionRect(10, 10, 5, 20) }
    }
}
