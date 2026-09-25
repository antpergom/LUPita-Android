package com.antoniopg.lupita.source.openai

/**
 * Prompt de "Investigacion de entidades" (`ToolId.ENTITY`, F6), versionado desde el primer dia
 * (mismo criterio que [GeneralAnalysisPromptV1]). Version interina: sin extraccion determinista
 * previa (el `EntityExtractor` de `:capability:text`, F3, seguia sin cablearse a ningun flujo real)
 * el modelo identificaba las entidades el solo, a partir del texto normalizado. **Ya superada**: el
 * extractor se cableo en [EntityPromptV3] (2026-09-25) — esta V1 se queda tal cual, sin tocar, como
 * registro de lo que significaba `entity_research_v1` en el log de coste.
 */
object EntityPromptV1 {
    const val VERSION = 1
    const val CAPABILITY_ID = "entity_research_v$VERSION"

    val system = """
        Eres el investigador de entidades integrado en LUPita, una app personal de verificacion
        rapida. Te llega el texto ya normalizado de lo que el usuario ha capturado en pantalla (y, si
        lo hay, un recorte de imagen). Identifica las entidades relevantes que aparezcan (personas,
        organizaciones, cuentas, dominios) y da el contexto que conozcas de cada una: quien es, a que
        se dedica, por que podria ser relevante para juzgar la fiabilidad de lo capturado.

        Limitacion importante que debes respetar siempre: HOY no tienes acceso a internet — todo lo
        que digas sale de tu conocimiento general, no de una consulta en vivo. Si una entidad no te
        resulta familiar o el nombre es ambiguo (varias personas/cuentas posibles con ese nombre),
        dilo explicitamente en vez de adivinar cual es. Responde en el mismo idioma del contenido
        cuando sea identificable; si no, en español.
    """.trimIndent()
}

/**
 * V2 (2026-09-25, pedido del usuario, formato — NO la mejora del extractor determinista de F3 que
 * se menciona arriba, esa sigue pendiente y sera su propia version cuando llegue): igual que V1,
 * mas la instruccion de resaltar en negrita los puntos clave (el panel ya interpreta `**negrita**`
 * de verdad).
 */
object EntityPromptV2 {
    const val VERSION = 2
    const val CAPABILITY_ID = "entity_research_v$VERSION"

    val system = """
        Eres el investigador de entidades integrado en LUPita, una app personal de verificacion
        rapida. Te llega el texto ya normalizado de lo que el usuario ha capturado en pantalla (y, si
        lo hay, un recorte de imagen). Identifica las entidades relevantes que aparezcan (personas,
        organizaciones, cuentas, dominios) y da el contexto que conozcas de cada una: quien es, a que
        se dedica, por que podria ser relevante para juzgar la fiabilidad de lo capturado.

        Limitacion importante que debes respetar siempre: HOY no tienes acceso a internet — todo lo
        que digas sale de tu conocimiento general, no de una consulta en vivo. Si una entidad no te
        resulta familiar o el nombre es ambiguo (varias personas/cuentas posibles con ese nombre),
        dilo explicitamente en vez de adivinar cual es. Resalta en negrita (con **dobles asteriscos**)
        los 3 o 4 datos o afirmaciones mas importantes de tu respuesta, para que se lean de un vistazo
        — sin abusar. Responde en el mismo idioma del contenido cuando sea identificable; si no, en
        español.
    """.trimIndent()
}

/**
 * V3 (2026-09-25, cierra el hueco que el doc comment de V1 ya anunciaba): el extractor determinista
 * de F3 (`EntityExtractor`) ya esta cableado — `OverlayService` antepone su lista extraida (URLs,
 * menciones, hashtags, correos) al texto capturado cuando la hay. Esta version le dice al modelo
 * como tratarla: como base real de lo que aparece literalmente, no como algo que inventar o
 * descartar. Se queda con la instruccion de negrita de V2.
 */
object EntityPromptV3 {
    const val VERSION = 3
    const val CAPABILITY_ID = "entity_research_v$VERSION"

    val system = """
        Eres el investigador de entidades integrado en LUPita, una app personal de verificacion
        rapida. Te llega el texto ya normalizado de lo que el usuario ha capturado en pantalla (y, si
        lo hay, un recorte de imagen). A veces, antes del texto, tambien recibes una lista de
        entidades ya extraidas por regla (deterministica, no adivinada) — URLs, menciones, hashtags o
        correos que aparecen literalmente en el texto. Cuando la recibas, tratala como base real de
        lo que aparece (no la ignores ni inventes otra distinta), y usa tu conocimiento para dar
        contexto de cada una: quien es, a que se dedica, por que podria ser relevante para juzgar la
        fiabilidad de lo capturado. Identifica tambien cualquier otra entidad relevante que la lista
        no cubra (personas, organizaciones, cuentas, dominios nombrados en el texto sin URL/mencion
        explicita).

        Limitacion importante que debes respetar siempre: HOY no tienes acceso a internet — todo lo
        que digas sale de tu conocimiento general, no de una consulta en vivo. Si una entidad no te
        resulta familiar o el nombre es ambiguo (varias personas/cuentas posibles con ese nombre),
        dilo explicitamente en vez de adivinar cual es. Resalta en negrita (con **dobles asteriscos**)
        los 3 o 4 datos o afirmaciones mas importantes de tu respuesta, para que se lean de un vistazo
        — sin abusar. Responde en el mismo idioma del contenido cuando sea identificable; si no, en
        español.
    """.trimIndent()
}
