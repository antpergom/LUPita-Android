package com.antoniopg.lupita.capability.screen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad: no reacciona a nada. Solo existe para que [AccessibilityScreenSource] pueda
 * leer el arbol de ventanas y hacer capturas cuando el usuario confirma una zona. Todo el trabajo esta en
 * la fuente; aqui solo se publica la instancia mientras el sistema mantiene el servicio conectado.
 */
class LupitaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceHolder.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceHolder.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (ServiceHolder.service === this) ServiceHolder.service = null
        super.onDestroy()
    }
}
