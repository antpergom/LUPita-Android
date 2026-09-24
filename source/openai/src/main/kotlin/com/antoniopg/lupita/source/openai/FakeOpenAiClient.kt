package com.antoniopg.lupita.source.openai

/**
 * Para probar quien consume [OpenAiClient] (el orquestador) sin tocar la red. Con una lista de
 * resultados se puede simular una secuencia (p. ej. un fallo transitorio y luego un exito, para
 * probar el reintento del orquestador) — se queda en el ultimo una vez agotada la lista.
 */
class FakeOpenAiClient(private val results: List<AnalysisResult>) : OpenAiClient {
    constructor(result: AnalysisResult) : this(listOf(result))

    var lastRequest: AnalysisRequest? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun analyze(request: AnalysisRequest, apiKey: String, model: String): AnalysisResult {
        lastRequest = request
        val result = results[callCount.coerceAtMost(results.size - 1)]
        callCount++
        return result
    }
}
