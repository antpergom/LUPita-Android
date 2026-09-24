package com.antoniopg.lupita.source.openai

import com.antoniopg.lupita.core.model.Depth

/**
 * Lo que se manda a analizar: texto ya normalizado (F2, `ContextNormalizer`) y, si lo hay, una imagen
 * ya codificada (F1.3, WebP) en base64. [depth] fija el esfuerzo de razonamiento pedido al modelo
 * (`reasoning.effort` en la Responses API) — mismos tres niveles que el presupuesto (F4).
 */
data class AnalysisRequest(val text: String, val imageWebpBase64: String? = null, val depth: Depth)

/** Nunca lanza: un fallo de red o de la API es [Failed], igual que `FetchResult` en `:capability:web`. */
sealed interface AnalysisResult {
    data class Success(
        val text: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedInputTokens: Int,
        val model: String,
    ) : AnalysisResult

    /**
     * [transient] distingue lo que merece un reintento (429, 5xx, fallo de red/timeout) de lo que
     * no (401/403 clave invalida, 400 request mal formada, etc.) — el orquestador (F4: "eso es del
     * orquestador, que decide cuando merece la pena volver a gastar") es quien reintenta, nunca el
     * cliente HTTP.
     */
    data class Failed(val reason: String, val transient: Boolean = false) : AnalysisResult
}
