package com.antoniopg.lupita.source.openai

import com.antoniopg.lupita.core.model.Depth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Construye el cuerpo JSON de `POST /responses` (verificado contra developers.openai.com/api/reference,
 * 2026-09-24 — no adivinado): `input` es una lista de items `{role, content[]}`, cada parte de contenido
 * es `input_text` o `input_image` (`image_url` como data URL, no hace falta subir la imagen a ningun
 * sitio publico). Puro: sin tocar la red, se prueba comparando el JSON resultante.
 */
object OpenAiRequestBuilder {
    // El prompt SIEMPRE sale de request.systemPrompt (2026-09-25) — antes era un parametro aparte
    // con valor por defecto, y ese defecto (GeneralAnalysisPromptV1) era lo que de verdad se enviaba
    // siempre, porque HttpOpenAiClient nunca lo sobreescribia. Ver AnalysisRequest.
    fun build(request: AnalysisRequest, model: String): JsonObject =
        buildJsonObject {
            put("model", model)
            putJsonArray("input") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        putJsonArray("content") {
                            add(textPart(request.systemPrompt))
                        }
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(textPart(request.text))
                            request.imageWebpBase64?.let { add(imagePart(it)) }
                        }
                    },
                )
            }
            put(
                "reasoning",
                buildJsonObject { put("effort", reasoningEffort(request.depth)) },
            )
            put("max_output_tokens", maxOutputTokens(request.depth))
        }

    private fun textPart(text: String) = buildJsonObject {
        put("type", "input_text")
        put("text", text)
    }

    private fun imagePart(webpBase64: String) = buildJsonObject {
        put("type", "input_image")
        put("image_url", "data:image/webp;base64,$webpBase64")
        put("detail", "auto")
    }

    private fun reasoningEffort(depth: Depth): String = when (depth) {
        Depth.LOW -> "low"
        Depth.MEDIUM -> "medium"
        Depth.HIGH -> "high"
    }

    /**
     * Tope de tokens de salida por profundidad — acota el coste maximo posible de una llamada (lo
     * usa tambien [OpenAiCost.worstCaseEstimate], misma fuente de verdad) y mantiene la respuesta
     * "breve" que pide [GeneralAnalysisPromptV1] incluso en profundidad alta.
     */
    fun maxOutputTokens(depth: Depth): Int = when (depth) {
        Depth.LOW -> 400
        Depth.MEDIUM -> 800
        Depth.HIGH -> 1500
    }
}
