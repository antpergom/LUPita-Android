package com.antoniopg.lupita.source.openai

import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.ModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiCostTest {
    private val luna = ModelOption(
        id = "gpt-6-luna",
        label = "Luna",
        inputUsdPerMillion = 0.10,
        cachedInputUsdPerMillion = 0.01,
        outputUsdPerMillion = 0.50,
    )
    private val mockWithoutPricing = ModelOption(id = "claude-sonnet", label = "Claude Sonnet")

    @Test
    fun `real cost splits cached and non-cached input at their own price`() {
        val result = AnalysisResult.Success(
            text = "x",
            inputTokens = 1_000_000,
            outputTokens = 0,
            cachedInputTokens = 400_000,
            model = "gpt-6-luna",
        )
        // 600k no-cache a 0.10/1M = 0.06 USD ; 400k cache a 0.01/1M = 0.004 USD
        assertEquals(CostMicros.ofUsd(0.064), OpenAiCost.of(result, luna))
    }

    @Test
    fun `real cost includes output tokens at the output price`() {
        val result = AnalysisResult.Success(text = "x", inputTokens = 0, outputTokens = 1_000_000, cachedInputTokens = 0, model = "m")

        assertEquals(CostMicros.ofUsd(0.50), OpenAiCost.of(result, luna))
    }

    @Test
    fun `a model without pricing never gets treated as free`() {
        val result = AnalysisResult.Success(text = "x", inputTokens = 100, outputTokens = 100, cachedInputTokens = 0, model = "m")

        assertNull(OpenAiCost.of(result, mockWithoutPricing))
    }

    @Test
    fun `worst case estimate is null without pricing, never zero`() {
        assertNull(OpenAiCost.worstCaseEstimate(promptChars = 100, textChars = 100, maxOutputTokens = 400, model = mockWithoutPricing))
    }

    @Test
    fun `worst case estimate covers both estimated input and the full output cap`() {
        // 400 caracteres de prompt+texto -> 100 tokens estimados (4 char/token); 400 tokens de salida.
        val estimate = OpenAiCost.worstCaseEstimate(promptChars = 200, textChars = 200, maxOutputTokens = 400, model = luna)

        // 100 tokens entrada * 0.10/1M + 400 tokens salida * 0.50/1M
        val expected = CostMicros.ofUsd(100 * 0.10 / 1_000_000.0) + CostMicros.ofUsd(400 * 0.50 / 1_000_000.0)
        assertEquals(expected, estimate)
    }
}
