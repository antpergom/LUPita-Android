package com.antoniopg.lupita.capability.text

/**
 * Bloque de contexto determinista para el prompt de Investigacion de entidades (cierra el hueco que
 * el propio doc comment de `EntityPromptV1` ya anunciaba, 2026-09-25): la lista de entidades YA
 * extraida por regla, para que el modelo no tenga que adivinarla desde cero — solo verificarla y
 * anadir contexto, sin descartar ni inventar otras de la nada. `null` si no hay nada que aportar
 * (las 4 listas vacias): el llamador no anade nada al texto en ese caso.
 */
fun ExtractedEntities.asPromptContext(): String? {
    if (isEmpty) return null
    return buildString {
        appendLine(
            "Entidades detectadas automaticamente en el texto (extraccion determinista por regla, NO " +
                "del modelo — tomalas como base real de lo que aparece, verificalas y anade contexto; " +
                "no las descartes ni inventes otras que no esten en esta lista o en el propio texto):",
        )
        if (mentions.isNotEmpty()) appendLine("- Menciones: ${mentions.joinToString(", ") { "@$it" }}")
        if (hashtags.isNotEmpty()) appendLine("- Hashtags: ${hashtags.joinToString(", ") { "#$it" }}")
        if (urls.isNotEmpty()) appendLine("- URLs: ${urls.joinToString(", ")}")
        if (emails.isNotEmpty()) appendLine("- Correos: ${emails.joinToString(", ")}")
    }.trimEnd()
}
