package com.antoniopg.lupita.source.openai

interface OpenAiClient {
    suspend fun analyze(request: AnalysisRequest, apiKey: String, model: String): AnalysisResult
}
