package com.antoniopg.lupita.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.Selection
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.StrokeRecorder
import com.antoniopg.lupita.core.model.TouchPoint
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaTheme

// rgba(10, 20, 22, .68) del mock.
private val Scrim = Color(0xAD0A1416)

// Tinta FIJA del boton de cancelar (#1C1A17 en el mock): la capa va siempre sobre un velo oscuro y no
// depende del tema. Con la tinta del tema, en oscuro salia crema sobre crema y el icono no se veia.
private val FixedInk = Color(0xFF1C1A17)

/**
 * Capa de captura (mock): la pantalla se atenua y el usuario dibuja a mano alzada la zona a analizar;
 * del trazo sale un rectangulo (ver [Selection]). Despues confirma o cancela.
 *
 * Mejora sobre el mock: se puede volver a dibujar sin cancelar. Un toque suelto (menos de 2 puntos)
 * cancela la captura solo si todavia no hay ninguna seleccion.
 *
 * Las coordenadas son las de la pantalla (la ventana la cubre entera), que son las que necesitara el
 * recorte de la captura real.
 */
@Composable
internal fun CaptureOverlay(
    screenWidth: Int,
    screenHeight: Int,
    minSizePx: Int,
    onCancel: () -> Unit,
    onConfirm: (SelectionRect) -> Unit,
) {
    LupitaTheme {
        val c = Lupita.colors
        val density = LocalDensity.current.density
        val cancel by rememberUpdatedState(onCancel)
        val recorder = remember { StrokeRecorder() }
        var stroke by remember { mutableStateOf(emptyList<TouchPoint>()) }
        var selected by remember { mutableStateOf<SelectionRect?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Antes se pedia `requireUnconsumed = false`, o sea que un toque sobre un boton
                        // tambien llegaba aqui. Por defecto, si un boton ya se lo ha quedado no es un trazo.
                        val down = awaitFirstDown()
                        // Cinturon y tirantes: un dedo real tiembla unos pixeles, asi que un toque sobre un
                        // boton se convertia en un trazo diminuto que sustituia la seleccion por un cuadrado
                        // del tamano minimo y se comia el clic del boton (visto en el dispositivo).
                        if (CaptureLayout.startsOnControl(down.position.round(), selected, screenWidth, density)) {
                            return@awaitEachGesture
                        }
                        recorder.reset()
                        recorder.add(down.position.x, down.position.y)
                        stroke = recorder.points
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    recorder.add(change.position.x, change.position.y)
                                    change.consume()
                                }
                            }
                            stroke = recorder.points
                        } while (event.changes.any { it.pressed })

                        val rect = Selection.fromStroke(recorder, minSizePx, screenWidth, screenHeight)
                        stroke = emptyList()
                        if (rect != null) {
                            selected = rect
                        } else if (selected == null) {
                            cancel()
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = c.accent,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                selected?.let { r ->
                    val topLeft = Offset(r.left.toFloat(), r.top.toFloat())
                    val size = Size(r.width.toFloat(), r.height.toFloat())
                    val radius = CornerRadius(16.dp.toPx())
                    drawRoundRect(c.accent.copy(alpha = 0.16f), topLeft, size, radius)
                    drawRoundRect(
                        color = c.accent,
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = radius,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                }
            }

            Text(
                text = stringResource(
                    if (selected == null) R.string.capture_hint_draw else R.string.capture_hint_selected,
                ),
                color = c.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = CaptureLayout.TOP_INSET_DP.dp),
            )

            val cancelText = stringResource(R.string.capture_cancel)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = CaptureLayout.CLOSE_TOP_DP.dp, end = CaptureLayout.CLOSE_END_DP.dp)
                    .size(CaptureLayout.CLOSE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = cancelText, tint = c.onAccent, modifier = Modifier.size(16.dp))
            }

            selected?.let { r ->
                // La misma geometria que decide donde un toque NO es un trazo (ver CaptureLayout).
                val buttons = CaptureLayout.actionButtons(r, screenWidth, density)
                Row(
                    modifier = Modifier.offset { IntOffset(buttons.left, buttons.top) },
                    horizontalArrangement = Arrangement.spacedBy(CaptureLayout.ACTION_GAP_DP.dp),
                ) {
                    ActionButton(Icons.Rounded.Close, cancelText, c.onAccent, FixedInk, onCancel)
                    ActionButton(
                        Icons.Rounded.Check,
                        stringResource(R.string.capture_confirm),
                        c.accent,
                        c.onAccent,
                    ) { onConfirm(r) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, description: String, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CaptureLayout.ACTION_SIZE_DP.dp)
            .shadow(6.dp, CircleShape)
            .background(background, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}
