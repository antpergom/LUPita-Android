package com.antoniopg.lupita.core.model

/** En que punto de la interaccion esta el overlay. */
enum class OverlayPhase { IDLE, MENU, CAPTURING }

/**
 * Transiciones PURAS del overlay: sin Android ni estado oculto, para poder probar cada regla
 * decidida con el usuario en JVM.
 */
object OverlayTransitions {

    /** Tap normal. En gris (sin herramientas) no hace nada; con el menu abierto, lo cierra. */
    fun onTap(phase: OverlayPhase, settings: BubbleSettings): OverlayPhase = when (phase) {
        OverlayPhase.IDLE -> if (settings.canCapture) OverlayPhase.CAPTURING else OverlayPhase.IDLE
        OverlayPhase.MENU -> OverlayPhase.IDLE
        OverlayPhase.CAPTURING -> OverlayPhase.CAPTURING
    }

    /** Pulsacion larga: abre el menu, TAMBIEN en gris (es la unica forma de activar herramientas). */
    fun onLongPress(phase: OverlayPhase): OverlayPhase = when (phase) {
        OverlayPhase.IDLE -> OverlayPhase.MENU
        OverlayPhase.MENU -> OverlayPhase.MENU
        OverlayPhase.CAPTURING -> OverlayPhase.CAPTURING
    }

    /** Tocar fuera del menu. */
    fun onDismissMenu(phase: OverlayPhase): OverlayPhase =
        if (phase == OverlayPhase.MENU) OverlayPhase.IDLE else phase

    /** El usuario termino o cancelo la captura. */
    fun onCaptureFinished(phase: OverlayPhase): OverlayPhase =
        if (phase == OverlayPhase.CAPTURING) OverlayPhase.IDLE else phase
}
