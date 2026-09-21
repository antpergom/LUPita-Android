package com.antoniopg.lupita.core.model

/**
 * Profundidad de investigacion, global a toda la ejecucion (no por herramienta). En F0 solo se
 * selecciona y se recuerda; que parametros concretos mueve cada nivel esta por estudiar.
 * Igual que [ToolId], [key] se persiste y no se renombra.
 */
enum class Depth(val key: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        /** De fabrica: baja. */
        val DEFAULT: Depth = LOW

        fun fromKey(key: String): Depth? = entries.firstOrNull { it.key == key }
    }
}
