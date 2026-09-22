package com.antoniopg.lupita.capability.web

sealed interface FetchResult {
    data class Success(val statusCode: Int, val contentType: String?, val body: String) : FetchResult
    data class Failed(val reason: String) : FetchResult
}

/**
 * Tras una interfaz, como `ScreenSource`: la logica que usa esto (`Readability`) se prueba sin red, con una
 * fuente falsa; solo `OkHttpWebFetcher` toca la red de verdad. Nunca lanza: un fallo de red es `Failed`.
 */
interface WebFetcher {
    suspend fun fetch(url: String): FetchResult
}
