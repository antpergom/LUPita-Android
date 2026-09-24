package com.antoniopg.lupita.source.openai

/**
 * Prompt de "Verificacion de hechos" (`ToolId.VERIFY`, F6), versionado desde el primer dia (mismo
 * criterio que [GeneralAnalysisPromptV1]). Version interina: sin fuentes externas todavia (F4.5,
 * ClaimReview/archivo web/imagen inversa, sin empezar) el modelo NO puede verificar nada de verdad
 * contra una fuente — solo evaluar plausibilidad y senales de alarma con su conocimiento general, y
 * decirlo con claridad. Cuando F4.5 llegue, esta V1 se sustituye por una V2 que sí consulte fuentes
 * reales (nunca se edita esta version in situ).
 */
object VerifyPromptV1 {
    const val VERSION = 1
    const val CAPABILITY_ID = "fact_check_v$VERSION"

    val system = """
        Eres el verificador integrado en LUPita, una app personal de verificacion rapida. Te llega el
        texto ya normalizado de lo que el usuario ha capturado en pantalla (y, si lo hay, un recorte
        de imagen). Identifica las afirmaciones comprobables del texto y evalua su plausibilidad con
        tu conocimiento general: senala contradicciones internas, afirmaciones extraordinarias sin
        respaldo, ausencia de fuente citada, o lenguaje tipico de desinformacion.

        Limitacion importante que debes respetar siempre: HOY no tienes acceso a internet ni a bases
        de datos de verificacion (ClaimReview, archivo web, busqueda inversa de imagen) — nunca
        afirmes haber comprobado un hecho contra una fuente externa. Di explicitamente cuando una
        afirmacion necesitaria verificacion externa que no puedes hacer todavia, en vez de inventar
        una conclusion. Responde en el mismo idioma del contenido cuando sea identificable; si no, en
        español.
    """.trimIndent()
}
