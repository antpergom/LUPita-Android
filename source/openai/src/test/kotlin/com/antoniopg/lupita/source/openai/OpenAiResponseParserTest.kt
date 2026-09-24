package com.antoniopg.lupita.source.openai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponseParserTest {
    // Forma real tomada de developers.openai.com/api/reference/resources/responses/methods/create (2026-09-24).
    private val realisticSuccess = """
        {
          "output": [
            {
              "type": "message",
              "role": "assistant",
              "content": [
                { "type": "output_text", "text": "El texto trata de..." }
              ]
            }
          ],
          "usage": {
            "input_tokens": 250,
            "output_tokens": 150,
            "input_tokens_details": { "cached_tokens": 30 }
          }
        }
    """.trimIndent()

    @Test
    fun `a realistic success body is parsed fully`() {
        val result = OpenAiResponseParser.parseSuccess(realisticSuccess, model = "gpt-6-luna") as AnalysisResult.Success

        assertEquals("El texto trata de...", result.text)
        assertEquals(250, result.inputTokens)
        assertEquals(150, result.outputTokens)
        assertEquals(30, result.cachedInputTokens)
        assertEquals("gpt-6-luna", result.model)
    }

    @Test
    fun `multiple output text parts are joined`() {
        val body = """
            {"output":[{"type":"message","content":[
                {"type":"output_text","text":"parte uno"},
                {"type":"output_text","text":"parte dos"}
            ]}]}
        """.trimIndent()

        val result = OpenAiResponseParser.parseSuccess(body, model = "m") as AnalysisResult.Success

        assertEquals("parte uno\nparte dos", result.text)
    }

    @Test
    fun `missing usage defaults every count to zero`() {
        val body = """{"output":[{"content":[{"text":"solo texto"}]}]}"""

        val result = OpenAiResponseParser.parseSuccess(body, model = "m") as AnalysisResult.Success

        assertEquals(0, result.inputTokens)
        assertEquals(0, result.outputTokens)
        assertEquals(0, result.cachedInputTokens)
    }

    @Test
    fun `an empty output is a failure, not a blank success`() {
        val result = OpenAiResponseParser.parseSuccess("""{"output":[]}""", model = "m")

        assertTrue(result is AnalysisResult.Failed)
    }

    @Test
    fun `malformed json never throws, it fails softly`() {
        val result = OpenAiResponseParser.parseSuccess("{not json", model = "m")

        assertTrue(result is AnalysisResult.Failed)
    }

    @Test
    fun `an error body's message is extracted`() {
        val body = """{"error":{"message":"Invalid API key","type":"invalid_request_error"}}"""

        assertEquals("Invalid API key", OpenAiResponseParser.parseErrorMessage(body))
    }

    @Test
    fun `a body without an error shape gives null, not a crash`() {
        assertEquals(null, OpenAiResponseParser.parseErrorMessage("""{"output":[]}"""))
        assertEquals(null, OpenAiResponseParser.parseErrorMessage("not json at all"))
    }
}
