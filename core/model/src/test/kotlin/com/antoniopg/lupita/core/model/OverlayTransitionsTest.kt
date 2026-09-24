package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTransitionsTest {

    private val gray = BubbleSettings()
    private val active = BubbleSettings(enabledTools = setOf(ToolId.GENERAL))

    @Test
    fun `factory settings are gray - no tools, low depth, no position`() {
        assertEquals(emptySet<ToolId>(), gray.enabledTools)
        assertEquals(Depth.LOW, gray.depth)
        assertEquals(null, gray.position)
        assertFalse(gray.canCapture)
    }

    @Test
    fun `a gray bubble never captures on tap - it opens the menu instead, as in the mock`() {
        val next = OverlayTransitions.onTap(OverlayPhase.IDLE, gray)

        assertEquals(OverlayPhase.MENU, next)
        assertFalse(next == OverlayPhase.CAPTURING)
    }

    @Test
    fun `an active bubble starts capturing on tap`() {
        assertEquals(OverlayPhase.CAPTURING, OverlayTransitions.onTap(OverlayPhase.IDLE, active))
    }

    @Test
    fun `enabling one tool turns the bubble active and disabling the last one turns it gray again`() {
        assertTrue(active.canCapture)
        assertFalse(active.copy(enabledTools = emptySet()).canCapture)
    }

    @Test
    fun `several tools can be active at once`() {
        val many = BubbleSettings(enabledTools = setOf(ToolId.GENERAL, ToolId.VERIFY, ToolId.ENTITY))
        assertTrue(many.canCapture)
        assertEquals(3, many.enabledTools.size)
    }

    @Test
    fun `long press opens the menu even when gray - it is the only way to enable tools`() {
        assertEquals(OverlayPhase.MENU, OverlayTransitions.onLongPress(OverlayPhase.IDLE))
    }

    @Test
    fun `tapping the bubble while the menu is open closes it`() {
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onTap(OverlayPhase.MENU, active))
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onTap(OverlayPhase.MENU, gray))
    }

    @Test
    fun `dismissing only affects an open menu`() {
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onDismissMenu(OverlayPhase.MENU))
        assertEquals(OverlayPhase.CAPTURING, OverlayTransitions.onDismissMenu(OverlayPhase.CAPTURING))
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onDismissMenu(OverlayPhase.IDLE))
    }

    @Test
    fun `while capturing, taps and long presses are ignored`() {
        assertEquals(OverlayPhase.CAPTURING, OverlayTransitions.onTap(OverlayPhase.CAPTURING, active))
        assertEquals(OverlayPhase.CAPTURING, OverlayTransitions.onLongPress(OverlayPhase.CAPTURING))
    }

    @Test
    fun `finishing or cancelling a capture returns to idle`() {
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onCaptureFinished(OverlayPhase.CAPTURING))
        assertEquals(OverlayPhase.MENU, OverlayTransitions.onCaptureFinished(OverlayPhase.MENU))
    }

    @Test
    fun `while results are shown, taps and long presses are ignored`() {
        assertEquals(OverlayPhase.RESULTS, OverlayTransitions.onTap(OverlayPhase.RESULTS, active))
        assertEquals(OverlayPhase.RESULTS, OverlayTransitions.onLongPress(OverlayPhase.RESULTS))
    }

    @Test
    fun `dismissing results only affects an open results panel`() {
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onDismissResults(OverlayPhase.RESULTS))
        assertEquals(OverlayPhase.MENU, OverlayTransitions.onDismissResults(OverlayPhase.MENU))
        assertEquals(OverlayPhase.IDLE, OverlayTransitions.onDismissResults(OverlayPhase.IDLE))
    }
}
