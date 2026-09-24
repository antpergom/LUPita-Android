package com.antoniopg.lupita.core.model

/** En que punto de la interaccion esta el overlay. */
enum class OverlayPhase { IDLE, MENU, CAPTURING, RESULTS }

/**
 * Transiciones PURAS del overlay: sin Android ni estado oculto, para poder probar cada regla
 * decidida con el usuario en JVM.
 */
object OverlayTransitions {

    /**
     * Tap normal. Con herramientas activas captura. En gris NO captura (no se puede ejecutar), pero
     * abre el menu, como en el mock: asi el usuario descubre como activar una herramienta. Con el
     * menu abierto, lo cierra.
     */
    fun onTap(phase: OverlayPhase, settings: BubbleSettings): OverlayPhase = when (phase) {
        OverlayPhase.IDLE -> if (settings.canCapture) OverlayPhase.CAPTURING else OverlayPhase.MENU
        OverlayPhase.MENU -> OverlayPhase.IDLE
        OverlayPhase.CAPTURING -> OverlayPhase.CAPTURING
        // Con el panel de resultados abierto, tocar la burbuja no hace nada (solo se cierra con su
        // propio boton) — igual que el mock, que solo reacciona al tap en fase 'idle'.
        OverlayPhase.RESULTS -> OverlayPhase.RESULTS
    }

    /** Pulsacion larga: abre el menu, TAMBIEN en gris (es la unica forma de activar herramientas). */
    fun onLongPress(phase: OverlayPhase): OverlayPhase = when (phase) {
        OverlayPhase.IDLE -> OverlayPhase.MENU
        OverlayPhase.MENU -> OverlayPhase.MENU
        OverlayPhase.CAPTURING -> OverlayPhase.CAPTURING
        OverlayPhase.RESULTS -> OverlayPhase.RESULTS
    }

    /** Tocar fuera del menu. */
    fun onDismissMenu(phase: OverlayPhase): OverlayPhase =
        if (phase == OverlayPhase.MENU) OverlayPhase.IDLE else phase

    /** El usuario termino o cancelo la captura. */
    fun onCaptureFinished(phase: OverlayPhase): OverlayPhase =
        if (phase == OverlayPhase.CAPTURING) OverlayPhase.IDLE else phase

    /** Cerrar el panel de resultados (boton de cerrar — F5/F6, panel en vivo). */
    fun onDismissResults(phase: OverlayPhase): OverlayPhase =
        if (phase == OverlayPhase.RESULTS) OverlayPhase.IDLE else phase
}
