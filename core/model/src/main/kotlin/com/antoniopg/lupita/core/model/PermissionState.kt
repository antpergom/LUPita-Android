package com.antoniopg.lupita.core.model

/**
 * Permisos sin los que la burbuja no puede funcionar. NOTIFICATIONS no es opcional: la notificacion
 * del servicio en primer plano es la UNICA via de apagar la burbuja (no hay gesto de descarte), asi
 * que sin ella no habria forma de cerrarla.
 */
enum class RequiredPermission { OVERLAY, NOTIFICATIONS }

data class PermissionState(val overlay: Boolean, val notifications: Boolean) {

    /** Los que faltan, en el orden en que se piden al usuario. */
    fun missing(): List<RequiredPermission> = buildList {
        if (!overlay) add(RequiredPermission.OVERLAY)
        if (!notifications) add(RequiredPermission.NOTIFICATIONS)
    }

    val allGranted: Boolean get() = missing().isEmpty()
}
