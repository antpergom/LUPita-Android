package com.antoniopg.lupita.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleGestureTest {

    private val gesture = BubbleGesture(touchSlop = 10f)

    @Test
    fun `a quick press and release is a tap`() {
        gesture.onDown(100f, 100f)

        assertEquals(GestureEvent.Tap, gesture.onUp())
    }

    @Test
    fun `movement under the slop still counts as a tap - fingers wobble`() {
        gesture.onDown(100f, 100f)
        assertNull(gesture.onMove(104f, 103f))

        assertEquals(GestureEvent.Tap, gesture.onUp())
    }

    @Test
    fun `moving past the slop starts a drag and reports the TOTAL offset from the touch point`() {
        gesture.onDown(100f, 100f)

        assertEquals(GestureEvent.Drag(30f, 0f), gesture.onMove(130f, 100f))
        assertEquals(GestureEvent.Drag(50f, 20f), gesture.onMove(150f, 120f))
    }

    @Test
    fun `releasing after a drag ends the drag and is not a tap`() {
        gesture.onDown(0f, 0f)
        gesture.onMove(50f, 0f)

        assertEquals(GestureEvent.DragEnd, gesture.onUp())
    }

    @Test
    fun `holding still fires a long press once, and releasing afterwards is not a tap`() {
        gesture.onDown(0f, 0f)

        assertEquals(GestureEvent.LongPress, gesture.onLongPressTimeout())
        assertNull(gesture.onLongPressTimeout())
        assertNull(gesture.onUp())
    }

    @Test
    fun `a long press timer that fires after a drag started is ignored`() {
        gesture.onDown(0f, 0f)
        gesture.onMove(50f, 0f)

        assertNull(gesture.onLongPressTimeout())
    }

    @Test
    fun `moving after a long press does not turn into a drag`() {
        gesture.onDown(0f, 0f)
        gesture.onLongPressTimeout()

        assertNull(gesture.onMove(80f, 80f))
    }

    @Test
    fun `cancel resets the gesture`() {
        gesture.onDown(0f, 0f)
        gesture.onMove(50f, 0f)
        gesture.onCancel()

        assertNull(gesture.onUp())
        assertNull(gesture.onLongPressTimeout())
    }

    @Test
    fun `gestures are independent - a drag does not leak into the next tap`() {
        gesture.onDown(0f, 0f)
        gesture.onMove(50f, 0f)
        gesture.onUp()

        gesture.onDown(200f, 200f)
        assertEquals(GestureEvent.Tap, gesture.onUp())
    }
}
