package com.antoniopg.lupita.ui.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Una ventana del `WindowManager` con contenido Compose. Todas las ventanas de la burbuja
 * (burbuja, menu y, en el paso 4, la capa de captura) comparten el mismo [OverlayLifecycleOwner].
 */
internal class ComposeOverlayWindow(
    context: Context,
    private val windowManager: WindowManager,
    owner: OverlayLifecycleOwner,
    val params: WindowManager.LayoutParams,
    content: @Composable () -> Unit,
    onTouch: ((View, MotionEvent) -> Boolean)? = null,
) {
    val view: ComposeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setContent(content)
        if (onTouch != null) setOnTouchListener(onTouch)
    }

    private var added = false

    fun add() {
        if (added) return
        windowManager.addView(view, params)
        added = true
    }

    fun update() {
        if (added) windowManager.updateViewLayout(view, params)
    }

    fun remove() {
        if (!added) return
        runCatching { windowManager.removeView(view) }
        added = false
    }
}
