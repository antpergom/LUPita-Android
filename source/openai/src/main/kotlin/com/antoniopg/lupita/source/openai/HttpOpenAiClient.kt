package com.antoniopg.lupita.source.openai

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * La unica implementacion que toca la red de verdad (igual que `OkHttpWebFetcher` en
 * `:capability:web`) — sin reintentos aqui: eso es del orquestador (F4), que decide cuando merece la
 * pena volver a gastar. Timeout de lectura alto porque el razonamiento de un modelo puede tardar.
 */
class HttpOpenAiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : OpenAiClient {

    override suspend fun analyze(request: AnalysisRequest, apiKey: String, model: String): AnalysisResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = OpenAiRequestBuilder.build(request, model).toString().toRequestBody(JSON)
                val httpRequest = Request.Builder()
                    .url("$BASE_URL/responses")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        AnalysisResult.Failed(OpenAiResponseParser.parseErrorMessage(raw) ?: "HTTP ${response.code}")
                    } else {
                        OpenAiResponseParser.parseSuccess(raw, model)
                    }
                }
            }.getOrElse { AnalysisResult.Failed(it.message ?: it::class.simpleName ?: "error desconocido") }
        }

    private companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        val JSON = "application/json".toMediaType()
    }
}
