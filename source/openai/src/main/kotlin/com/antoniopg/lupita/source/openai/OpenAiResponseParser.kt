package com.antoniopg.lupita.source.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lee el cuerpo JSON de una respuesta de `POST /responses` (verificado contra la doc real, 2026-09-24):
 * el texto vive en `output[].content[].text` (items `type: "message"`, partes `type: "output_text"`);
 * el uso de tokens en `usage.input_tokens`/`usage.output_tokens`/
 * `usage.input_tokens_details.cached_tokens`. Nunca lanza: cualquier forma inesperada es [AnalysisResult.Failed].
 */
object OpenAiResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSuccess(body: String, model: String): AnalysisResult = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val text = root["output"]?.jsonArray.orEmpty()
            .asSequence()
            .mapNotNull { it.jsonObject["content"]?.jsonArray }
            .flatten()
            .mapNotNull { part -> part.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n")
            .trim()

        if (text.isBlank()) return AnalysisResult.Failed("respuesta sin texto")

        val usage = root["usage"]?.jsonObject
        AnalysisResult.Success(
            text = text,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            cachedInputTokens = usage?.get("input_tokens_details")?.jsonObject
                ?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            model = model,
        )
    }.getOrElse { AnalysisResult.Failed(it.message ?: "respuesta ilegible") }

    /** Cuerpo de error de la API (`{"error":{"message":...}}`) — `null` si no tiene esa forma. */
    fun parseErrorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
