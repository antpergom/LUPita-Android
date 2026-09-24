package com.antoniopg.lupita.source.openai

/** Para probar quien consume [OpenAiClient] (el orquestador, mas adelante) sin tocar la red. */
class FakeOpenAiClient(private val result: AnalysisResult) : OpenAiClient {
    var lastRequest: AnalysisRequest? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun analyze(request: AnalysisRequest, apiKey: String, model: String): AnalysisResult {
        lastRequest = request
        callCount++
        return result
    }
}
