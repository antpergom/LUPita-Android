package com.antoniopg.lupita.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antoniopg.lupita.core.model.BubblePosition
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.OverlayPhase
import com.antoniopg.lupita.core.model.OverlayTransitions
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.core.model.ToolId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * La burbuja flotante: una ventana `TYPE_APPLICATION_OVERLAY` con una `ComposeView`.
 *
 * Es el "host" por superposicion (opcion A, ver docs/decisions/...overlay-superposicion...). El
 * contenido es Compose puro y el gesto y las reglas viven en clases sin Android, de modo que un host
 * alternativo (p. ej. por accesibilidad) solo tendria que reimplementar la creacion de la ventana.
 *
 * Sin foco (`FLAG_NOT_FOCUSABLE`): en reposo no debe robar el teclado ni el foco a la app de debajo.
 */
class BubbleOverlay(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = BubbleGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val bubbleSizePx = (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt()

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var collectJob: Job? = null
    private var startX = 0
    private var startY = 0

    private var current by mutableStateOf(BubbleSettings())
    private var phase by mutableStateOf(OverlayPhase.IDLE)

    private val longPressTimer = Runnable { gesture.onLongPressTimeout()?.let(::handle) }

    fun show() {
        if (view != null) return
        collectJob = scope.launch {
            // La primera lectura decide la posicion inicial; despues se sigue observando.
            val first = settings.settings.first()
            current = first
            attach(first.position ?: defaultPosition())
            settings.settings.collect { current = it }
        }
    }

    fun remove() {
        collectJob?.cancel()
        handler.removeCallbacks(longPressTimer)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
        lifecycleOwner.destroy()
    }

    private fun attach(position: BubblePosition) {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent { BubbleContent(active = current.canCapture) }
            setOnTouchListener(::onTouch)
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // LAYOUT_IN_SCREEN: x/y se miden desde la esquina de la PANTALLA. Sin ella cuentan desde
            // debajo de la barra de estado (visto en el dispositivo: 145 px de desfase) y el recorte a
            // los limites de la pantalla dejaba sacar la burbuja por abajo.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
        lifecycleOwner.start()
        windowManager.addView(composeView, layoutParams)
        view = composeView
        params = layoutParams
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        val p = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.onDown(event.rawX, event.rawY)
                startX = p.x
                startY = p.y
                handler.postDelayed(longPressTimer, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> gesture.onMove(event.rawX, event.rawY)?.let(::handle)
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressTimer)
                gesture.onUp()?.let { if (it == GestureEvent.Tap) v.performClick(); handle(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressTimer)
                gesture.onCancel()
            }
        }
        return true
    }

    private fun handle(event: GestureEvent) {
        when (event) {
            is GestureEvent.Drag -> {
                handler.removeCallbacks(longPressTimer)
                moveTo(startX + event.dx.toInt(), startY + event.dy.toInt())
            }
            GestureEvent.DragEnd -> snapToEdgeAndRemember()
            GestureEvent.Tap -> onTap()
            GestureEvent.LongPress -> onLongPress()
        }
    }

    private fun onTap() {
        phase = OverlayTransitions.onTap(phase, current)
        Log.d(TAG, "tap -> $phase (canCapture=${current.canCapture})")
        if (phase == OverlayPhase.CAPTURING) {
            // ANDAMIO (paso 3b): la captura real llega en el paso 4.
            Toast.makeText(context, R.string.overlay_capture_pending, Toast.LENGTH_SHORT).show()
            phase = OverlayTransitions.onCaptureFinished(phase)
        }
    }

    private fun onLongPress() {
        // ANDAMIO (paso 3b): aqui va el menu desplegable (paso 3c). Mientras tanto la pulsacion larga
        // alterna una herramienta, solo para poder comprobar gris/activa y la persistencia.
        val enabled = ToolId.GENERAL in current.enabledTools
        Log.d(TAG, "long press -> scaffold toggles GENERAL (was enabled=$enabled)")
        scope.launch { settings.setToolEnabled(ToolId.GENERAL, !enabled) }
    }

    private fun moveTo(x: Int, y: Int) {
        val p = params ?: return
        val v = view ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = v.width.takeIf { it > 0 } ?: bubbleSizePx
        val h = v.height.takeIf { it > 0 } ?: bubbleSizePx
        p.x = x.coerceIn(0, (bounds.width() - w).coerceAtLeast(0))
        p.y = y.coerceIn(0, (bounds.height() - h).coerceAtLeast(0))
        windowManager.updateViewLayout(v, p)
    }

    /** Al soltar, se pega al borde horizontal mas cercano y se recuerda la posicion. */
    private fun snapToEdgeAndRemember() {
        val p = params ?: return
        val v = view ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = v.width.takeIf { it > 0 } ?: bubbleSizePx
        val edgeX = if (p.x + w / 2 < bounds.width() / 2) 0 else bounds.width() - w
        moveTo(edgeX, p.y)
        val settled = BubblePosition(p.x, p.y)
        scope.launch { settings.setPosition(settled) }
    }

    private fun defaultPosition(): BubblePosition {
        val bounds = windowManager.currentWindowMetrics.bounds
        return BubblePosition(x = bounds.width() - bubbleSizePx, y = bounds.height() / 3)
    }

    private companion object {
        const val TAG = "LupitaOverlay"
        const val BUBBLE_SIZE_DP = 56
    }
}

/** Aspecto provisional (gris = sin herramientas, color = lista) hasta que llegue el mock. */
@androidx.compose.runtime.Composable
private fun BubbleContent(active: Boolean) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        val background = if (active) MaterialTheme.colorScheme.primary else Color(0xFF8A8A8F)
        val foreground = if (active) MaterialTheme.colorScheme.onPrimary else Color(0xFFE6E6EA)
        Canvas(modifier = Modifier.size(56.dp)) {
            drawCircle(color = background)
            val stroke = size.minDimension * 0.075f
            val radius = size.minDimension * 0.19f
            val lens = Offset(size.width * 0.44f, size.height * 0.44f)
            drawCircle(color = foreground, radius = radius, center = lens, style = Stroke(width = stroke))
            drawLine(
                color = foreground,
                start = Offset(lens.x + radius * 0.72f, lens.y + radius * 0.72f),
                end = Offset(size.width * 0.72f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
