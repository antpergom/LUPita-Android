package com.antoniopg.lupita.capability.screen

/** El servicio activo, si lo hay. Android crea el servicio por su cuenta, asi que no se puede inyectar. */
internal object ServiceHolder {
    @Volatile
    var service: LupitaAccessibilityService? = null
}
