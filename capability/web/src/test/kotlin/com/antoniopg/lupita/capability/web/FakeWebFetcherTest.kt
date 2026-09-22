package com.antoniopg.lupita.capability.web

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeWebFetcherTest {

    @Test
    fun `returns the configured response for a known url and counts the call`() = runTest {
        val fetcher = FakeWebFetcher(mapOf("https://x.com" to FetchResult.Success(200, "text/html", "<html></html>")))

        val result = fetcher.fetch("https://x.com")

        assertTrue(result is FetchResult.Success)
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun `an unconfigured url fails instead of crashing, and still counts`() = runTest {
        val fetcher = FakeWebFetcher()

        val result = fetcher.fetch("https://sin-configurar.com") as FetchResult.Failed

        assertTrue(result.reason.contains("sin-configurar.com"))
        assertEquals(1, fetcher.calls)
    }
}
