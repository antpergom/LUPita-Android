package com.antoniopg.lupita.core.model

/**
 * Las cuatro herramientas (burbujas). [key] es lo que se PERSISTE en el dispositivo: renombrar una
 * clave dejaria sin efecto la configuracion ya guardada del usuario, asi que no se toca nunca.
 */
enum class ToolId(val key: String) {
    GENERAL("general"),
    VERIFY("verify"),
    AI_DETECT("ai_detect"),
    ENTITY("entity"),
    ;

    companion object {
        /** `null` si la clave no corresponde a ninguna herramienta (dato antiguo o corrupto). */
        fun fromKey(key: String): ToolId? = entries.firstOrNull { it.key == key }
    }
}
