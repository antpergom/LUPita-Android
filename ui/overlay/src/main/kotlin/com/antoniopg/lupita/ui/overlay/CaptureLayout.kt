package com.antoniopg.lupita.ui.overlay

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.antoniopg.lupita.core.model.SelectionRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Geometria de los controles de la capa de captura, en pixeles. La usan TANTO el dibujo de los botones
 * como la zona donde un toque NO es un trazo: al salir de la misma funcion, no pueden desalinearse.
 *
 * Existe porque un dedo real tiembla unos pixeles: un toque sobre un boton llegaba al detector de
 * trazos como un trazo diminuto, que sustituia la seleccion por un cuadrado del tamano minimo y se
 * comia el clic del boton (visto en el dispositivo).
 */
internal object CaptureLayout {
    /** Espacio reservado arriba para no dibujar bajo la barra de estado (~41 dp en un Pixel). */
    const val TOP_INSET_DP = 56

    const val CLOSE_SIZE_DP = 34
    const val CLOSE_TOP_DP = TOP_INSET_DP - 8
    const val CLOSE_END_DP = 12

    const val ACTION_SIZE_DP = 40
    const val ACTION_GAP_DP = 10
    const val ACTIONS_WIDTH_DP = ACTION_SIZE_DP * 2 + ACTION_GAP_DP

    /** Separacion entre el rectangulo y los botones, y margen minimo con el borde superior. */
    const val ACTIONS_ABOVE_DP = 50
    const val ACTIONS_TOP_MARGIN_DP = 8

    /** Reserva a la derecha para que la fila de botones no se salga de la pantalla. */
    const val ACTIONS_RIGHT_RESERVE_DP = 96

    /** Un toque a menos de esto de un control se considera del control (el dedo no es un puntero). */
    const val CONTROL_MARGIN_DP = 12

    private fun px(dp: Int, density: Float) = (dp * density).roundToInt()

    /** El boton de cerrar de arriba a la derecha. */
    fun closeButton(screenWidth: Int, density: Float): IntRect {
        val size = px(CLOSE_SIZE_DP, density)
        val right = screenWidth - px(CLOSE_END_DP, density)
        val top = px(CLOSE_TOP_DP, density)
        return IntRect(right - size, top, right, top + size)
    }

    /** La fila cancelar/confirmar: encima del rectangulo, pegada al borde de arriba y sin salirse por la derecha. */
    fun actionButtons(selection: SelectionRect, screenWidth: Int, density: Float): IntRect {
        val x = min(selection.left, screenWidth - px(ACTIONS_RIGHT_RESERVE_DP, density))
        val y = max(px(ACTIONS_TOP_MARGIN_DP, density), selection.top - px(ACTIONS_ABOVE_DP, density))
        return IntRect(x, y, x + px(ACTIONS_WIDTH_DP, density), y + px(ACTION_SIZE_DP, density))
    }

    /** Zonas donde empezar un toque NO inicia un trazo: cada control, ensanchado por el margen. */
    fun controlZones(selection: SelectionRect?, screenWidth: Int, density: Float): List<IntRect> {
        val margin = px(CONTROL_MARGIN_DP, density)
        return listOfNotNull(
            closeButton(screenWidth, density),
            selection?.let { actionButtons(it, screenWidth, density) },
        ).map { it.inflate(margin) }
    }

    fun startsOnControl(point: IntOffset, selection: SelectionRect?, screenWidth: Int, density: Float): Boolean =
        controlZones(selection, screenWidth, density).any { it.contains(point) }
}
