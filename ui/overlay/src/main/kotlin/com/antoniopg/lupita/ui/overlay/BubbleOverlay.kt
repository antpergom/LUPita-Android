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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.BubblePosition
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.OverlayPhase
import com.antoniopg.lupita.core.model.OverlayTransitions
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.core.model.ToolId
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * La burbuja flotante y su menu: ventanas `TYPE_APPLICATION_OVERLAY` con contenido Compose.
 *
 * Es el "host" por superposicion (opcion A, ver docs/decisions/...overlay-superposicion...). El
 * contenido es Compose puro y las reglas y el gesto viven en clases sin Android, de modo que un host
 * alternativo (p. ej. por accesibilidad) solo tendria que reimplementar la creacion de las ventanas.
 *
 * Son DOS ventanas: la de la burbuja (pequena, sin foco: en reposo no roba el teclado ni el foco a la
 * app de debajo) y, mientras el menu esta abierto, otra a pantalla completa para que tocar fuera lo
 * cierre.
 */
class BubbleOverlay(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val onOpenApp: (AppSection) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = BubbleGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    private val density = context.resources.displayMetrics.density
    private val marginPx = (BUBBLE_MARGIN_DP * density).roundToInt()
    private val bubblePx = (BUBBLE_SIZE_DP * density).roundToInt()
    private val windowPx = bubblePx + 2 * marginPx

    private var bubbleWindow: ComposeOverlayWindow? = null
    private var menuWindow: ComposeOverlayWindow? = null
    private var collectJob: Job? = null
    private var started = false
    private var startX = 0
    private var startY = 0
    private var phase = OverlayPhase.IDLE

    private var current by mutableStateOf(BubbleSettings())

    private val longPressTimer = Runnable { gesture.onLongPressTimeout()?.let(::handle) }

    fun show() {
        if (bubbleWindow != null || collectJob != null) return
        collectJob = scope.launch {
            // La primera lectura decide la posicion inicial; despues se sigue observando.
            val first = settings.settings.first()
            current = first
            attachBubble(first.position ?: defaultPosition())
            settings.settings.collect { current = it }
        }
    }

    fun remove() {
        collectJob?.cancel()
        collectJob = null
        handler.removeCallbacks(longPressTimer)
        menuWindow?.remove()
        menuWindow = null
        bubbleWindow?.remove()
        bubbleWindow = null
        if (started) owner.destroy()
        started = false
        phase = OverlayPhase.IDLE
    }

    private fun attachBubble(position: BubblePosition) {
        if (!started) {
            owner.start()
            started = true
        }
        val params = WindowManager.LayoutParams(
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
        val window = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = params,
            content = { BubbleContent(activeCount = current.enabledTools.size) },
            onTouch = ::onTouch,
        )
        window.add()
        bubbleWindow = window
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        val p = bubbleWindow?.params ?: return false
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
                gesture.onUp()?.let {
                    if (it == GestureEvent.Tap) v.performClick()
                    handle(it)
                }
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
            GestureEvent.LongPress -> setPhase(OverlayTransitions.onLongPress(phase))
        }
    }

    private fun onTap() {
        setPhase(OverlayTransitions.onTap(phase, current))
        if (phase == OverlayPhase.CAPTURING) {
            // ANDAMIO (paso 3): la captura real llega en el paso 4.
            Toast.makeText(context, R.string.overlay_capture_pending, Toast.LENGTH_SHORT).show()
            setPhase(OverlayTransitions.onCaptureFinished(phase))
        }
    }

    private fun setPhase(next: OverlayPhase) {
        val previous = phase
        if (next == previous) return
        phase = next
        Log.d(TAG, "phase $previous -> $next (canCapture=${current.canCapture})")
        if (next == OverlayPhase.MENU) showMenu() else if (previous == OverlayPhase.MENU) hideMenu()
    }

    private fun showMenu() {
        val p = bubbleWindow?.params ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val visible = IntRect(
            left = p.x + marginPx,
            top = p.y + marginPx,
            right = p.x + marginPx + bubblePx,
            bottom = p.y + marginPx + bubblePx,
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Sin NOT_TOUCH_MODAL: esta ventana SI captura los toques de fuera del panel (cierran el menu).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        menuWindow = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = params,
            content = {
                MenuOverlay(
                    settings = current,
                    anchor = visible,
                    growFromRight = visible.center.x > bounds.width() / 2,
                    onDismiss = { setPhase(OverlayTransitions.onDismissMenu(phase)) },
                    onToggleTool = ::toggleTool,
                    onSelectDepth = ::selectDepth,
                    onOpenSettings = { openApp(AppSection.SETTINGS) },
                    onOpenHistory = { openApp(AppSection.HISTORY) },
                )
            },
        ).also { it.add() }
    }

    private fun hideMenu() {
        menuWindow?.remove()
        menuWindow = null
    }

    private fun toggleTool(tool: ToolId, enabled: Boolean) {
        Log.d(TAG, "menu toggle $tool -> $enabled")
        scope.launch { settings.setToolEnabled(tool, enabled) }
    }

    private fun selectDepth(depth: Depth) {
        Log.d(TAG, "menu depth -> $depth")
        scope.launch { settings.setDepth(depth) }
    }

    private fun openApp(section: AppSection) {
        Log.d(TAG, "menu open app -> $section")
        setPhase(OverlayPhase.IDLE)
        onOpenApp(section)
    }

    private fun moveTo(x: Int, y: Int) {
        val window = bubbleWindow ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = window.view.width.takeIf { it > 0 } ?: windowPx
        val h = window.view.height.takeIf { it > 0 } ?: windowPx
        window.params.x = x.coerceIn(0, (bounds.width() - w).coerceAtLeast(0))
        window.params.y = y.coerceIn(0, (bounds.height() - h).coerceAtLeast(0))
        window.update()
    }

    /** Al soltar, se pega al borde horizontal mas cercano y se recuerda la posicion. */
    private fun snapToEdgeAndRemember() {
        val window = bubbleWindow ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = window.view.width.takeIf { it > 0 } ?: windowPx
        val edgeX = if (window.params.x + w / 2 < bounds.width() / 2) 0 else bounds.width() - w
        moveTo(edgeX, window.params.y)
        val settled = BubblePosition(window.params.x, window.params.y)
        scope.launch { settings.setPosition(settled) }
    }

    private fun defaultPosition(): BubblePosition {
        val bounds = windowManager.currentWindowMetrics.bounds
        return BubblePosition(x = bounds.width() - windowPx, y = bounds.height() / 3)
    }

    private companion object {
        const val TAG = "LupitaOverlay"
    }
}
