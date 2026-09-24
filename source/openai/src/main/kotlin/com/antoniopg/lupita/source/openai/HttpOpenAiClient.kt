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
                        val message = OpenAiResponseParser.parseErrorMessage(raw) ?: "HTTP ${response.code}"
                        // 429 (limite de tasa) y 5xx (fallo del lado del servidor) son los unicos casos
                        // donde repetir la MISMA request tiene sentido; un 4xx normal (clave invalida,
                        // request mal formada) va a fallar otra vez exactamente igual.
                        AnalysisResult.Failed(message, transient = response.code == 429 || response.code in 500..599)
                    } else {
                        OpenAiResponseParser.parseSuccess(raw, model)
                    }
                }
            }.getOrElse {
                // Timeout, conexion perdida, DNS... nunca se sabe si la request llego a procesarse o
                // no, pero repetirla es razonable (a diferencia de un 4xx, que es un rechazo seguro).
                AnalysisResult.Failed(it.message ?: it::class.simpleName ?: "error desconocido", transient = true)
            }
        }

    private companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        val JSON = "application/json".toMediaType()
    }
}
