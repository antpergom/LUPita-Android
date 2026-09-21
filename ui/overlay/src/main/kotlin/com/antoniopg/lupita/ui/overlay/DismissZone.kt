package com.antoniopg.lupita.ui.overlay

import kotlin.math.hypot

/**
 * La zona de «soltar aqui para quitar» que aparece abajo, centrada, mientras se arrastra la burbuja. Kotlin
 * puro para poder probar la geometria en JVM. Un area de acierto mas grande que el circulo dibujado hace facil
 * dar en el blanco con el dedo.
 */
internal object DismissZone {
    const val TARGET_SIZE_DP = 64

    /** Distancia del centro del objetivo al borde inferior de la pantalla (deja libre la barra de gestos). */
    const val BOTTOM_OFFSET_DP = 128

    /** Radio de acierto alrededor del centro del objetivo. */
    const val HIT_RADIUS_DP = 88

    fun centerX(screenWidth: Int): Float = screenWidth / 2f

    fun centerY(screenHeight: Int, density: Float): Float = screenHeight - BOTTOM_OFFSET_DP * density

    /** `true` si el punto (el centro de la burbuja, en pixeles de pantalla) esta sobre el objetivo. */
    fun contains(x: Float, y: Float, screenWidth: Int, screenHeight: Int, density: Float): Boolean =
        hypot(x - centerX(screenWidth), y - centerY(screenHeight, density)) <= HIT_RADIUS_DP * density
}
