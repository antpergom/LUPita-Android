package com.antoniopg.lupita.source.openai

import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.ModelOption
import kotlin.math.ceil

/**
 * Convierte tokens de OpenAI a [CostMicros] (F4) con el precio de [ModelOption] (F5, decision
 * 2026-09-24). Un precio ausente (`null`, entrada mock sin proveedor real) nunca se trata como
 * gratis: [of] y [worstCaseEstimate] devuelven `null` — el llamador debe negarse a facturar/llamar,
 * no asumir coste cero.
 */
object OpenAiCost {

    /** Coste real, a partir del uso que devuelve la API. `cachedInputTokens` es un SUBCONJUNTO de
     * `inputTokens` (facturado aparte, mas barato) segun `usage.input_tokens_details.cached_tokens`. */
    fun of(result: AnalysisResult.Success, model: ModelOption): CostMicros? {
        val inputPrice = model.inputUsdPerMillion ?: return null
        val cachedPrice = model.cachedInputUsdPerMillion ?: return null
        val outputPrice = model.outputUsdPerMillion ?: return null
        val nonCachedInput = (result.inputTokens - result.cachedInputTokens).coerceAtLeast(0)
        return micros(nonCachedInput, inputPrice) + micros(result.cachedInputTokens, cachedPrice) + micros(result.outputTokens, outputPrice)
    }

    /**
     * Cota superior de seguridad para el chequeo de presupuesto ANTES de llamar — nunca para
     * facturar (eso siempre sale de [of] con el uso real). Estimacion GRUESA de tokens de entrada
     * (~4 caracteres/token) mas el tope de tokens de salida ya fijado en la request
     * ([OpenAiRequestBuilder.maxOutputTokens]): el coste real nunca puede superar esto por
     * construccion, salvo que la API facture distinto de lo documentado.
     *
     * [hasImage] (2026-09-25, cierra el hueco de que la imagen nunca se enviaba): OpenAI no publica
     * la formula exacta de tokens de imagen de GPT-6 Luna, asi que se usa [IMAGE_TOKENS_ESTIMATE],
     * una cota fija deliberadamente generosa (recortes de seleccion, nunca la pantalla entera) en
     * vez de calcularla por dimension real — sigue el mismo principio de "mejor sobreestimar" que el
     * resto de esta funcion. Revisar si OpenAI publica el desglose real para este modelo.
     */
    fun worstCaseEstimate(promptChars: Int, textChars: Int, maxOutputTokens: Int, model: ModelOption, hasImage: Boolean = false): CostMicros? {
        val inputPrice = model.inputUsdPerMillion ?: return null
        val outputPrice = model.outputUsdPerMillion ?: return null
        val inputTokens = estimateTokens(promptChars + textChars) + (if (hasImage) IMAGE_TOKENS_ESTIMATE else 0)
        return micros(inputTokens, inputPrice) + micros(maxOutputTokens, outputPrice)
    }

    private fun estimateTokens(chars: Int): Int = ceil(chars / 4.0).toInt()

    private fun micros(tokens: Int, usdPerMillion: Double): CostMicros = CostMicros.ofUsd(tokens * usdPerMillion / 1_000_000.0)

    private const val IMAGE_TOKENS_ESTIMATE = 1_500
}
