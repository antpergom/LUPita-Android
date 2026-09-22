package com.antoniopg.lupita.capability.web

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * La unica implementacion que toca la red de verdad. Sin reintentos (eso es del orquestador, F4, que
 * decide cuando gastar); un `User-Agent` identificable en vez de suplantar un navegador.
 */
class OkHttpWebFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : WebFetcher {

    override suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                FetchResult.Success(response.code, response.header("Content-Type"), body)
            }
        }.getOrElse { FetchResult.Failed(it.message ?: it::class.simpleName ?: "error desconocido") }
    }

    private companion object {
        const val USER_AGENT = "LUPita/1.0 (app Android personal, sin uso comercial)"
    }
}
