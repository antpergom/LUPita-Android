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
import android.view.WindowInsets
import android.view.WindowManager
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
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.SettingsRepository
import com.antoniopg.lupita.core.model.ToolId
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Tamano minimo de la zona seleccionada (mock: 60). */
private const val MIN_SELECTION_DP = 60

/**
 * La burbuja flotante, su menu y la capa de captura: ventanas `TYPE_APPLICATION_OVERLAY` con
 * contenido Compose.
 *
 * Es el "host" por superposicion (opcion A, ver docs/decisions/...overlay-superposicion...). El
 * contenido es Compose puro y las reglas y el gesto viven en clases sin Android, de modo que un host
 * alternativo (p. ej. por accesibilidad) solo tendria que reimplementar la creacion de las ventanas.
 *
 * La burbuja es una ventana pequena y sin foco (en reposo no roba el teclado ni el foco a la app de
 * debajo). El menu y la captura abren otra a pantalla completa, que captura los toques.
 *
 * [onCapture] recibe el rectangulo confirmado, en pixeles de pantalla. La capa de captura ya se ha
 * retirado cuando se llama, para que la captura real (F1) no la incluya.
 */
class BubbleOverlay(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val onOpenApp: (AppSection) -> Unit,
    private val onCapture: (SelectionRect) -> Unit,
    /** Quitar la burbuja (arrastrandola a la X o con el boton del panel): lo resuelve quien la creo. */
    private val onQuit: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = BubbleGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    private val density = context.resources.displayMetrics.density
    private val marginPx = (BUBBLE_MARGIN_DP * density).roundToInt()
    private val bubblePx = (BUBBLE_SIZE_DP * density).roundToInt()
    private val windowPx = bubblePx + 2 * marginPx
    private val minSelectionPx = (MIN_SELECTION_DP * density).roundToInt()

    private var bubbleWindow: ComposeOverlayWindow? = null
    private var menuWindow: ComposeOverlayWindow? = null
    private var captureWindow: ComposeOverlayWindow? = null
    private var dismissWindow: ComposeOverlayWindow? = null
    private var dismissActive by mutableStateOf(false)
    private var collectJob: Job? = null
    private var started = false
    private var startX = 0
    private var startY = 0
    private var phase = OverlayPhase.IDLE

    private var current by mutableStateOf(BubbleSettings())

    private val longPressTimer = Runnable { gesture.onLongPressTimeout()?.let(::handle) }

    /**
     * Oculta/muestra el icono de la burbuja sin quitar su ventana — usado por `OverlayService`
     * justo alrededor de la captura de pixeles real, para que la burbuja no salga en su propia
     * captura (bug real encontrado verificando F5/F6 en el Pixel, 2026-09-25).
     */
    fun setBubbleVisible(visible: Boolean) {
        bubbleWindow?.setVisible(visible)
    }

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
        hideDismissTarget()
        captureWindow?.remove()
        captureWindow = null
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

    /** Ventana a pantalla completa que SI captura los toques (menu y captura). */
    private fun fullScreenParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Sin NOT_TOUCH_MODAL: los toques de fuera del contenido tambien llegan aqui.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Sin esto una ventana MATCH_PARENT respeta el hueco de las barras del sistema y su origen queda
        // 145 px por debajo del de la pantalla (visto en el dispositivo: un trazo en (300,800) llegaba
        // como (300,655)). La captura real (F1) necesita coordenadas de PANTALLA exactas.
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
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
                hideDismissTarget()
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
                showDismissTarget()
                dismissActive = bubbleOverDismissTarget()
            }
            GestureEvent.DragEnd -> {
                val quit = bubbleOverDismissTarget()
                hideDismissTarget()
                if (quit) onQuit() else snapToEdgeAndRemember()
            }
            GestureEvent.Tap -> setPhase(OverlayTransitions.onTap(phase, current))
            GestureEvent.LongPress -> setPhase(OverlayTransitions.onLongPress(phase))
        }
    }

    private fun setPhase(next: OverlayPhase) {
        val previous = phase
        if (next == previous) return
        phase = next
        Log.d(TAG, "phase $previous -> $next (canCapture=${current.canCapture})")
        if (previous == OverlayPhase.MENU) hideMenu()
        if (previous == OverlayPhase.CAPTURING) hideCapture()
        if (next == OverlayPhase.MENU) showMenu()
        if (next == OverlayPhase.CAPTURING) showCapture()
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
        menuWindow = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = fullScreenParams(),
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
                    onQuit = onQuit,
                )
            },
        ).also { it.add() }
    }

    private fun hideMenu() {
        menuWindow?.remove()
        menuWindow = null
    }

    private fun showCapture() {
        val bounds = windowManager.currentWindowMetrics.bounds
        captureWindow = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = fullScreenParams(),
            content = {
                CaptureOverlay(
                    screenWidth = bounds.width(),
                    screenHeight = bounds.height(),
                    minSizePx = minSelectionPx,
                    onCancel = ::cancelCapture,
                    onConfirm = ::confirmCapture,
                )
            },
        ).also { it.add() }
    }

    private fun hideCapture() {
        captureWindow?.remove()
        captureWindow = null
    }

    private fun cancelCapture() {
        Log.d(TAG, "capture cancelled")
        setPhase(OverlayTransitions.onCaptureFinished(phase))
    }

    private fun confirmCapture(rect: SelectionRect) {
        Log.d(TAG, "capture confirmed [${rect.left},${rect.top}][${rect.right},${rect.bottom}] ${rect.width}x${rect.height}")
        // Primero se retira la capa (setPhase), despues se avisa: la captura real no debe incluirla.
        setPhase(OverlayTransitions.onCaptureFinished(phase))
        onCapture(rect)
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

    /** El centro de la burbuja esta sobre la X de abajo (mientras se arrastra o al soltar). */
    private fun bubbleOverDismissTarget(): Boolean {
        val window = bubbleWindow ?: return false
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = window.view.width.takeIf { it > 0 } ?: windowPx
        val h = window.view.height.takeIf { it > 0 } ?: windowPx
        return DismissZone.contains(
            window.params.x + w / 2f, window.params.y + h / 2f, bounds.width(), bounds.height(), density,
        )
    }

    /** La X aparece al empezar a arrastrar y solo se dibuja: no recibe toques. */
    private fun showDismissTarget() {
        if (dismissWindow != null) return
        val bounds = windowManager.currentWindowMetrics.bounds
        val sizePx = ((DismissZone.TARGET_SIZE_DP + 2 * DISMISS_MARGIN_DP) * density).roundToInt()
        val params = WindowManager.LayoutParams(
            sizePx, sizePx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (DismissZone.centerX(bounds.width()) - sizePx / 2f).roundToInt()
            y = (DismissZone.centerY(bounds.height(), density) - sizePx / 2f).roundToInt()
        }
        dismissWindow = ComposeOverlayWindow(
            context = context,
            windowManager = windowManager,
            owner = owner,
            params = params,
            content = { DismissTargetContent(active = dismissActive) },
        ).also { it.add() }
    }

    private fun hideDismissTarget() {
        dismissActive = false
        dismissWindow?.remove()
        dismissWindow = null
    }

    /** Al soltar, se pega al borde horizontal mas cercano y se recuerda la posicion. */
    private fun snapToEdgeAndRemember() {
        val window = bubbleWindow ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = window.view.width.takeIf { it > 0 } ?: windowPx
        val gap = edgeGapPx()
        val edgeX = if (window.params.x + w / 2 < bounds.width() / 2) gap else bounds.width() - w - gap
        moveTo(edgeX, window.params.y)
        val settled = BubblePosition(window.params.x, window.params.y)
        scope.launch { settings.setPosition(settled) }
    }

    private fun defaultPosition(): BubblePosition {
        val bounds = windowManager.currentWindowMetrics.bounds
        return BubblePosition(x = bounds.width() - windowPx - edgeGapPx(), y = bounds.height() / 3)
    }

    /**
     * Separacion extra entre la ventana de la burbuja y el borde de la pantalla, para que tocar la burbuja
     * no dispare el gesto de navegacion (atras) del sistema: lo que falte hasta el margen de gestos del
     * sistema (ya descontado el margen propio de la ventana) mas [EDGE_EXTRA_DP].
     */
    private fun edgeGapPx(): Int {
        val gestures = windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.systemGestures())
        val system = maxOf(gestures.left, gestures.right)
        return maxOf(system - marginPx, 0) + (EDGE_EXTRA_DP * density).roundToInt()
    }

    private companion object {
        const val TAG = "LupitaOverlay"

        /** Holgura adicional sobre el margen de gestos del sistema (pedida por el usuario: un par de pixeles). */
        const val EDGE_EXTRA_DP = 2
    }
}
