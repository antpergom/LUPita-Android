package com.antoniopg.lupita.core.model

/** Un idioma de la app. [displayName] va en su propio idioma (endonimo): no se traduce. */
data class LanguageOption(val tag: String, val displayName: String)

object Languages {
    val supported: List<LanguageOption> = listOf(
        LanguageOption("es", "Español"),
        LanguageOption("en", "English"),
    )

    /** Idioma de respaldo cuando ni el elegido ni el del sistema estan soportados. */
    const val FALLBACK = "es"

    /**
     * El idioma que la app esta usando: el elegido por el usuario (`appTag`) o, si no ha elegido, el del
     * sistema; si tampoco esta soportado, [FALLBACK]. Solo cuenta el idioma, no la region (`es-ES` -> `es`).
     */
    fun resolve(appTag: String?, systemTag: String?): String =
        supportedLanguage(appTag) ?: supportedLanguage(systemTag) ?: FALLBACK

    private fun supportedLanguage(tag: String?): String? {
        val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
        return language.takeIf { code -> supported.any { it.tag == code } }
    }
}
