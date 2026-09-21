package com.antoniopg.lupita.core.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class TouchPoint(val x: Float, val y: Float)

/** Caja que encierra todos los puntos de un trazo, en pixeles de pantalla. */
data class StrokeBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

/** Rectangulo de pantalla en pixeles. Es lo que se recorta de la captura para analizarlo. */
data class SelectionRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right >= left && bottom >= top) { "rectangulo invertido: $this" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Acumula un trazo a mano alzada. La caja ([bounds]) se calcula con TODOS los puntos, pero solo se
 * conservan los ultimos [maxKeptPoints] para dibujar: un trazo largo no debe hacer que el rectangulo
 * salga de los ultimos puntos (el mock de Claude Design tenia justo ese fallo, guardaba 80 y calculaba
 * la caja con ellos).
 */
class StrokeRecorder(private val maxKeptPoints: Int = 400) {
    private val kept = ArrayDeque<TouchPoint>()
    private var minX = Float.POSITIVE_INFINITY
    private var minY = Float.POSITIVE_INFINITY
    private var maxX = Float.NEGATIVE_INFINITY
    private var maxY = Float.NEGATIVE_INFINITY

    var count: Int = 0
        private set

    val points: List<TouchPoint> get() = kept.toList()

    fun add(x: Float, y: Float) {
        minX = min(minX, x)
        minY = min(minY, y)
        maxX = max(maxX, x)
        maxY = max(maxY, y)
        count++
        kept.addLast(TouchPoint(x, y))
        if (kept.size > maxKeptPoints) kept.removeFirst()
    }

    fun bounds(): StrokeBounds? = if (count == 0) null else StrokeBounds(minX, minY, maxX, maxY)

    fun reset() {
        kept.clear()
        minX = Float.POSITIVE_INFINITY
        minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY
        maxY = Float.NEGATIVE_INFINITY
        count = 0
    }
}

object Selection {

    /**
     * El rectangulo de un trazo: la caja del trazo, sin ajustarse a ningun elemento ni inferir nada
     * (decision del usuario). Si queda por debajo de [minSizePx] en algun eje, **crece hasta el minimo
     * manteniendo el centro**; y si al crecer se sale de la pantalla, se desplaza hacia dentro sin
     * cambiar el tamano (asi sigue conteniendo el trazo).
     *
     * `null` si el trazo tiene menos de 2 puntos: un toque suelto no es una seleccion.
     */
    fun fromStroke(recorder: StrokeRecorder, minSizePx: Int, screenWidth: Int, screenHeight: Int): SelectionRect? {
        if (recorder.count < 2) return null
        val b = recorder.bounds() ?: return null
        val (left, right) = fit(b.minX, b.maxX, minSizePx, screenWidth)
        val (top, bottom) = fit(b.minY, b.maxY, minSizePx, screenHeight)
        return SelectionRect(left, top, right, bottom)
    }

    /** Ajusta un eje: redondea hacia fuera, recorta a la pantalla, aplica el minimo conservando el centro. */
    private fun fit(lo: Float, hi: Float, minSize: Int, limit: Int): Pair<Int, Int> {
        var a = floor(lo).toInt().coerceIn(0, limit)
        var b = ceil(hi).toInt().coerceIn(0, limit)
        val required = min(minSize, limit) // el minimo no puede ser mayor que la propia pantalla
        if (b - a < required) {
            val missing = required - (b - a)
            a -= missing / 2
            b += missing - missing / 2
            if (a < 0) {
                b -= a
                a = 0
            }
            if (b > limit) {
                a -= b - limit
                b = limit
            }
        }
        return a to b
    }
}
