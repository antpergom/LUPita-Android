package com.antoniopg.lupita.core.model

/**
 * Secciones de la app a las que se salta desde el menu de la burbuja. [key] viaja en el Intent que
 * abre la Activity: no se renombra.
 */
enum class AppSection(val key: String) {
    SETTINGS("settings"),
    HISTORY("history"),
    ;

    companion object {
        fun fromKey(key: String?): AppSection? = entries.firstOrNull { it.key == key }
    }
}
