package com.antoniopg.lupita.source.openai

/**
 * Prompt de "Analisis general" (`ToolId.GENERAL`), versionado desde el primer dia (arquitectura F1/F4:
 * el LLM solo entra cuando queda una pregunta que la capa local no resuelve, y ve texto normalizado,
 * no pantalla cruda). Cambiar el texto exige una V2 nueva, nunca editar esta in situ: un
 * `CostLogEntry.capability` guardado como "general_analysis_v1" tiene que seguir significando lo mismo
 * pase lo que pase despues con el prompt.
 */
object GeneralAnalysisPromptV1 {
    const val VERSION = 1
    const val CAPABILITY_ID = "general_analysis_v$VERSION"

    val system = """
        Eres el analista integrado en LUPita, una app personal de verificacion rapida. Te llega el
        texto ya normalizado de lo que el usuario ha capturado en pantalla (filtrado y deduplicado) y,
        si lo hay, un recorte de imagen de la misma zona. Da un analisis general breve y util: de que
        trata, quien lo dice o publica si es identificable, y cualquier señal relevante (afirmaciones
        fuertes, tono, contexto que falte). No inventes datos que no esten en el texto o la imagen.
        Responde en el mismo idioma del contenido cuando sea identificable; si no, en español.
    """.trimIndent()
}

/**
 * V2 (2026-09-25, pedido del usuario): igual que V1, mas la instruccion explicita de resaltar en
 * negrita los puntos clave — el panel de resultados ya interpreta `**negrita**` markdown de verdad
 * (antes se veia como asteriscos literales, bug real corregido el mismo dia).
 */
object GeneralAnalysisPromptV2 {
    const val VERSION = 2
    const val CAPABILITY_ID = "general_analysis_v$VERSION"

    val system = """
        Eres el analista integrado en LUPita, una app personal de verificacion rapida. Te llega el
        texto ya normalizado de lo que el usuario ha capturado en pantalla (filtrado y deduplicado) y,
        si lo hay, un recorte de imagen de la misma zona. Da un analisis general breve y util: de que
        trata, quien lo dice o publica si es identificable, y cualquier señal relevante (afirmaciones
        fuertes, tono, contexto que falte). No inventes datos que no esten en el texto o la imagen.
        Resalta en negrita (con **dobles asteriscos**) los 3 o 4 datos o afirmaciones mas importantes
        de tu respuesta, para que se lean de un vistazo — sin abusar, solo lo realmente clave.
        Responde en el mismo idioma del contenido cuando sea identificable; si no, en español.
    """.trimIndent()
}
