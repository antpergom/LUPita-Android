package com.antoniopg.lupita.ui.overlay

import kotlin.math.hypot

sealed interface GestureEvent {
    data object Tap : GestureEvent
    data object LongPress : GestureEvent

    /** Desplazamiento TOTAL desde el punto donde se puso el dedo (no incremental). */
    data class Drag(val dx: Float, val dy: Float) : GestureEvent

    data object DragEnd : GestureEvent
}

/**
 * Distingue tap, pulsacion larga y arrastre a partir de coordenadas de PANTALLA (`rawX`/`rawY`).
 * Kotlin puro: el temporizador de la pulsacion larga lo pone quien llama y avisa con
 * [onLongPressTimeout].
 *
 * Por que coordenadas de pantalla y no las de Compose: al arrastrar se mueve la propia ventana, asi
 * que las coordenadas locales al dedo cambian bajo sus pies y el desplazamiento sale mal (salta).
 */
class BubbleGesture(private val touchSlop: Float) {

    private enum class State { IDLE, DOWN, DRAGGING, LONG_PRESSED }

    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f

    fun onDown(x: Float, y: Float) {
        state = State.DOWN
        downX = x
        downY = y
    }

    fun onMove(x: Float, y: Float): GestureEvent? {
        val dx = x - downX
        val dy = y - downY
        return when (state) {
            State.DOWN ->
                if (hypot(dx, dy) > touchSlop) {
                    state = State.DRAGGING
                    GestureEvent.Drag(dx, dy)
                } else {
                    null
                }
            State.DRAGGING -> GestureEvent.Drag(dx, dy)
            // Tras una pulsacion larga el gesto ya no es un arrastre.
            State.IDLE, State.LONG_PRESSED -> null
        }
    }

    /** Solo cuenta si el dedo sigue puesto y quieto; tras un arrastre no hay pulsacion larga. */
    fun onLongPressTimeout(): GestureEvent? =
        if (state == State.DOWN) {
            state = State.LONG_PRESSED
            GestureEvent.LongPress
        } else {
            null
        }

    fun onUp(): GestureEvent? {
        val result = when (state) {
            State.DOWN -> GestureEvent.Tap
            State.DRAGGING -> GestureEvent.DragEnd
            State.IDLE, State.LONG_PRESSED -> null
        }
        state = State.IDLE
        return result
    }

    fun onCancel() {
        state = State.IDLE
    }
}
