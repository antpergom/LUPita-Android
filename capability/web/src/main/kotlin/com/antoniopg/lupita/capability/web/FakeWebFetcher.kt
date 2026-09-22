package com.antoniopg.lupita.capability.web

/** Para tests: fija la respuesta por URL y cuenta las llamadas, igual que `FakeScreenSource`. */
class FakeWebFetcher(private val responses: Map<String, FetchResult> = emptyMap()) : WebFetcher {

    var calls = 0
        private set

    override suspend fun fetch(url: String): FetchResult {
        calls++
        return responses[url] ?: FetchResult.Failed("sin configurar: $url")
    }
}
