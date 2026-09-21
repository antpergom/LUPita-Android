package com.antoniopg.lupita.core.model

data class BubblePosition(val x: Int, val y: Int)

/**
 * Configuracion de la burbuja que PERSISTE entre ejecuciones (decidido): herramientas activas,
 * profundidad y posicion. De fabrica: ninguna herramienta, profundidad baja, posicion por defecto.
 */
data class BubbleSettings(
    val enabledTools: Set<ToolId> = emptySet(),
    val depth: Depth = Depth.DEFAULT,
    /** `null` = aun no movida por el usuario: la burbuja usa su posicion inicial. */
    val position: BubblePosition? = null,
) {
    /** Gris = ninguna herramienta activa: el tap no hace nada. Es el unico estado que bloquea. */
    val canCapture: Boolean get() = enabledTools.isNotEmpty()
}
