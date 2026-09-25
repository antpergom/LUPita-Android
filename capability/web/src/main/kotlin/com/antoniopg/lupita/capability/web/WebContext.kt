package com.antoniopg.lupita.capability.web

/**
 * Bloque de contexto con el contenido REAL de una URL citada en el texto capturado — primer paso
 * real hacia F4.5 (fuentes externas), aprovechando lo que F3 ya construyo (`WebFetcher`/
 * `Readability`) para Verificacion de hechos. A diferencia de las demas senales deterministas de
 * F3, esto SI es una fuente externa de verdad: el modelo puede citarla como tal, no es su
 * conocimiento general ni una inferencia sobre la imagen. `null` si no hay texto principal legible
 * (pagina vacia, con paywall, o que no parseo bien) — el llamador no anade nada en ese caso, para no
 * fingir una fuente que en realidad no aporto nada.
 */
fun ReadableContent.asPromptContext(url: String): String? {
    if (mainText.isBlank()) return null
    return buildString {
        appendLine("Contenido real obtenido de $url (fetch automatico, puedes citarlo como fuente externa consultada):")
        metadata.title?.let { appendLine("Titulo: $it") }
        append(mainText.take(MAX_CHARS))
    }.trimEnd()
}

private const val MAX_CHARS = 2_000
