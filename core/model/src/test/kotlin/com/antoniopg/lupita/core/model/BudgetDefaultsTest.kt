package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetDefaultsTest {

    private val validJson = """
        {
          "depths": [
            { "depth": "low", "costLimitUsd": 0.05, "maxPaidCalls": 3 },
            { "depth": "medium", "costLimitUsd": 0.20, "maxPaidCalls": 8 },
            { "depth": "high", "costLimitUsd": 1.00, "maxPaidCalls": 30 }
          ],
          "tools": [
            { "tool": "general", "limitUsd": 2.0 },
            { "tool": "verify", "limitUsd": 3.0 },
            { "tool": "ai_detect", "limitUsd": 4.0 },
            { "tool": "entity", "limitUsd": 5.0 }
          ],
          "globalLimitUsd": 50.0,
          "globalPeriod": "daily"
        }
    """.trimIndent()

    @Test
    fun `a well formed catalog is parsed as-is`() {
        val defaults = BudgetDefaults.parse(validJson)

        assertEquals(CostMicros.ofUsd(0.05), defaults.depthBudget(Depth.LOW).costLimit)
        assertEquals(3, defaults.depthBudget(Depth.LOW).maxPaidCalls)
        assertEquals(CostMicros.ofUsd(1.00), defaults.depthBudget(Depth.HIGH).costLimit)
        assertEquals(CostMicros.ofUsd(3.0), defaults.toolLimit(ToolId.VERIFY))
        assertEquals(CostMicros.ofUsd(50.0), defaults.globalLimit)
        assertEquals(BudgetPeriod.DAILY, defaults.globalPeriod)
    }

    @Test
    fun `malformed json falls back to the built-in defaults instead of crashing`() {
        assertEquals(BudgetDefaults.FALLBACK, BudgetDefaults.parse("{not json"))
        assertEquals(BudgetDefaults.FALLBACK, BudgetDefaults.parse(""))
    }

    @Test
    fun `a depth missing from the file falls back for just that depth`() {
        val onlyLow = """{"depths":[{"depth":"low","costLimitUsd":0.05,"maxPaidCalls":3}]}"""

        val defaults = BudgetDefaults.parse(onlyLow)

        assertEquals(CostMicros.ofUsd(0.05), defaults.depthBudget(Depth.LOW).costLimit)
        assertEquals(BudgetDefaults.FALLBACK.depthBudget(Depth.HIGH), defaults.depthBudget(Depth.HIGH))
    }

    @Test
    fun `an unknown depth or tool key is ignored, not crashed on`() {
        val withGarbage = """{"depths":[{"depth":"nope","costLimitUsd":9,"maxPaidCalls":9}]}"""

        assertEquals(BudgetDefaults.FALLBACK.depthBudget(Depth.LOW), BudgetDefaults.parse(withGarbage).depthBudget(Depth.LOW))
    }

    @Test
    fun `every depth and tool always has a value, even from an empty file`() {
        val defaults = BudgetDefaults.parse("{}")

        Depth.entries.forEach { assertEquals(BudgetDefaults.FALLBACK.depthBudget(it), defaults.depthBudget(it)) }
        ToolId.entries.forEach { assertEquals(BudgetDefaults.FALLBACK.toolLimit(it), defaults.toolLimit(it)) }
    }
}
