package com.antoniopg.lupita.capability.image

/**
 * Bloque de contexto determinista para el prompt de Deteccion de IA (cierra el hueco que el propio
 * doc comment de `AiDetectPromptV1` ya anunciaba, 2026-09-25): la senal C2PA YA detectada
 * (estructural, nunca verificada — ver `C2paDetector`), para que el modelo no finja poder verla por
 * si mismo. A diferencia de las entidades, aqui SIEMPRE hay algo que decir (presencia o ausencia son
 * ambas informacion), asi que nunca devuelve `null`.
 */
fun C2paDetection.asPromptContext(): String = buildString {
    append("Senal determinista (no del modelo, no concluyente por si sola): ")
    if (present) {
        append("la imagen SI contiene un manifiesto C2PA embebido")
        label?.let { append(" (etiqueta: $it)") }
        append(
            ". Esto no prueba que el contenido sea o no sea IA — algunas herramientas de edicion o " +
                "generacion adjuntan procedencia C2PA, pero tambien lo hacen camaras y apps sin relacion " +
                "con IA. No se ha verificado la firma ni la cadena de certificados, solo que el " +
                "contenedor existe.",
        )
    } else {
        append(
            "la imagen NO contiene ningun manifiesto C2PA detectable. La ausencia tampoco prueba nada " +
                "por si sola — la inmensa mayoria del contenido real, generado o no, no lleva C2PA hoy.",
        )
    }
}
