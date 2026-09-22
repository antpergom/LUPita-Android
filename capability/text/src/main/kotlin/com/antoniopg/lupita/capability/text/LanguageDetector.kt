package com.antoniopg.lupita.capability.text

/** Idioma detectado en un texto capturado (no confundir con `Languages`/`LanguageOption`: eso es el idioma de la UI). */
enum class DetectedLanguage(val tag: String) {
    SPANISH("es"),
    ENGLISH("en"),
    FRENCH("fr"),
    GERMAN("de"),
    PORTUGUESE("pt"),

    /** Muy poco texto, o el resultado esta empatado o por debajo del umbral: no se afirma nada. */
    UNKNOWN("und"),
}

/**
 * Deteccion determinista de idioma por frecuencia de palabras funcionales (articulos, preposiciones...):
 * sin modelo, sin red. Cubre 5 idiomas a proposito, no todos — mas vale `UNKNOWN` que un idioma inventado.
 * Ver docs/decisions/2026-09-21-privacidad-de-lo-capturado.md: nunca se infiere de mas.
 */
object LanguageDetector {

    /** Palabras funcionales, no vocabulario de contenido: aparecen en cualquier texto real de ese idioma. */
    private val STOPWORDS: Map<DetectedLanguage, Set<String>> = mapOf(
        DetectedLanguage.SPANISH to setOf(
            "el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "es", "por", "para", "con",
            "no", "se", "su", "al", "lo", "como", "del", "las", "pero", "mas", "muy",
        ),
        DetectedLanguage.ENGLISH to setOf(
            "the", "of", "and", "to", "in", "is", "you", "that", "it", "he", "was", "for", "on", "are",
            "with", "as", "his", "they", "at", "be", "this", "have", "from", "but", "not",
        ),
        DetectedLanguage.FRENCH to setOf(
            "le", "la", "les", "de", "des", "et", "un", "une", "est", "que", "pour", "dans", "ce", "il",
            "vous", "avec", "pas", "sur", "je", "au", "du", "mais", "plus", "tout",
        ),
        DetectedLanguage.GERMAN to setOf(
            "der", "die", "das", "und", "ist", "nicht", "ein", "eine", "zu", "den", "mit", "auf", "des",
            "dem", "sich", "von", "fur", "im", "aber", "auch", "sind", "wie",
        ),
        DetectedLanguage.PORTUGUESE to setOf(
            "o", "a", "os", "as", "de", "que", "e", "em", "um", "uma", "por", "para", "com", "nao",
            "se", "seu", "ao", "do", "como", "mas", "mais", "muito", "esta",
        ),
    )

    /** Menos palabras que esto es demasiado poco para afirmar nada. */
    private const val MIN_WORDS = 4

    /** Fraccion minima de palabras reconocidas para no quedarse en UNKNOWN. */
    private const val MIN_SCORE = 0.15

    fun detect(text: String): DetectedLanguage {
        val words = text.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotBlank() }
        if (words.size < MIN_WORDS) return DetectedLanguage.UNKNOWN

        val scores = STOPWORDS.mapValues { (_, stop) -> words.count { it in stop } }
        val best = scores.values.max()
        if (best == 0 || best.toDouble() / words.size < MIN_SCORE) return DetectedLanguage.UNKNOWN

        // Empate entre dos idiomas: no se elige uno a ciegas.
        val winners = scores.filterValues { it == best }.keys
        return winners.singleOrNull() ?: DetectedLanguage.UNKNOWN
    }
}
