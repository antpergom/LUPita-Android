package com.antoniopg.lupita.capability.text

/** Lo que aparece en el texto tal cual, sin resolver nada (un `@usuario` no se comprueba que exista). */
data class ExtractedEntities(
    val urls: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = urls.isEmpty() && mentions.isEmpty() && hashtags.isEmpty() && emails.isEmpty()
}

/**
 * Extractores por regla, no por modelo: solo lo que tiene una forma reconocible sin ambiguedad. Se queda
 * fuera, a proposito, cualquier cosa con muchos falsos positivos (p. ej. telefonos: eso ya lo cubre
 * `Redactor`, en `:capability:privacy`, con el proposito distinto de ocultar, no de extraer).
 */
object EntityExtractor {

    private val URL = Regex("""\bhttps?://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""\b[A-Za-z0-9][\w.+-]*@[A-Za-z0-9][\w-]*\.[A-Za-z]{2,}\b""")

    // Un @ o # que no venga pegado a otra letra/digito/guion bajo (para no partir un email o un handle a medias).
    private val MENTION = Regex("""(?<![\w@.])@(\w{2,30})""")
    private val HASHTAG = Regex("""(?<![\w#])#([\p{L}0-9_]{2,50})""")

    fun extract(text: String): ExtractedEntities {
        // Los correos se sacan ANTES de buscar menciones: si no, "ana@example.com" dejaria "example.com"
        // leido como si fuera el resto de una mencion.
        val withoutEmails = EMAIL.replace(text) { " " }
        return ExtractedEntities(
            urls = matches(URL, text),
            mentions = matches(MENTION, withoutEmails) { it.groupValues[1] },
            hashtags = matches(HASHTAG, text) { it.groupValues[1] },
            emails = matches(EMAIL, text),
        )
    }

    private fun matches(regex: Regex, text: String, group: (MatchResult) -> String = { it.value }): List<String> =
        regex.findAll(text).map(group).distinct().toList()
}
