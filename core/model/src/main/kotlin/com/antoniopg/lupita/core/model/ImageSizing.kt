package com.antoniopg.lupita.core.model

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Tamano de la imagen que se envia (decidido 2026-09-22): solo se reduce, nunca se amplia (DeepSeek ya
 * agranda lo pequeno por su cuenta), con un tope por AREA y no por lado largo: una captura vertical
 * queda mas legible con los mismos tokens. Ver docs/decisions/2026-09-22-imagen-enviada-formato-y-almacenamiento.md.
 */
object ImageSizing {
    /** ~1300 x 1300 px: el tamano al que DeepSeek reescala. */
    const val MAX_AREA_PX: Long = 1300L * 1300L

    data class Size(val width: Int, val height: Int)

    fun fit(width: Int, height: Int, maxArea: Long = MAX_AREA_PX): Size {
        require(width > 0 && height > 0) { "imagen vacia: ${width}x$height" }
        val area = width.toLong() * height
        if (area <= maxArea) return Size(width, height)
        val scale = sqrt(maxArea.toDouble() / area)
        return Size(
            width = floor(width * scale).toInt().coerceAtLeast(1),
            height = floor(height * scale).toInt().coerceAtLeast(1),
        )
    }
}
