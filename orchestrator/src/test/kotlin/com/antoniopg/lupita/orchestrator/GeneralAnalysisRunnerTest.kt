package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.AiCredentials
import com.antoniopg.lupita.core.model.AiCredentialsRepository
import com.antoniopg.lupita.core.model.BudgetDefaults
import com.antoniopg.lupita.core.model.BudgetPeriod
import com.antoniopg.lupita.core.model.BudgetSettings
import com.antoniopg.lupita.core.model.BudgetSettingsRepository
import com.antoniopg.lupita.core.model.CostLogEntry
import com.antoniopg.lupita.core.model.CostLogRepository
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.DepthBudget
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.ResourceClass
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.source.openai.AnalysisResult
import com.antoniopg.lupita.source.openai.FakeOpenAiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAiCredentialsRepository(apiKey: String?) : AiCredentialsRepository {
    override val credentials: Flow<AiCredentials?> = flowOf(apiKey?.let { AiCredentials(it) })
    override suspend fun save(credentials: AiCredentials) = error("no usado en este test")
    override suspend fun clear() = error("no usado en este test")
}

private class FakeBudgetSettingsRepository(initial: BudgetSettings = BudgetSettings()) : BudgetSettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun setDepthBudget(depth: Depth, budget: DepthBudget) = error("no usado en este test")
    override suspend fun setToolLimit(tool: ToolId, limit: CostMicros) = error("no usado en este test")
    override suspend fun setGlobalLimit(limit: CostMicros) = error("no usado en este test")
    override suspend fun setGlobalPeriod(period: BudgetPeriod) = error("no usado en este test")
    override suspend fun resetToDefaults() = error("no usado en este test")
}

private class FakeCostLogRepository : CostLogRepository {
    val recorded = mutableListOf<CostLogEntry>()
    override suspend fun record(entry: CostLogEntry) {
        recorded += entry
    }
    override suspend fun entriesSince(sinceMillis: Long) = recorded.filter { it.timestampMillis >= sinceMillis }
    override fun observeRecent(limit: Int) = MutableStateFlow(recorded.take(limit))
}

class GeneralAnalysisRunnerTest {
    private val luna = ModelOption("gpt-6-luna", "Luna", 0.10, 0.01, 0.50)
    private val defaults = BudgetDefaults.FALLBACK

    private fun runner(
        apiKey: String? = "sk-test",
        budgetSettings: BudgetSettings = BudgetSettings(),
        costLog: FakeCostLogRepository = FakeCostLogRepository(),
        openAi: FakeOpenAiClient = FakeOpenAiClient(
            AnalysisResult.Success(text = "analisis", inputTokens = 100, outputTokens = 50, cachedInputTokens = 0, model = "gpt-6-luna"),
        ),
    ) = GeneralAnalysisRunner(
        aiCredentials = FakeAiCredentialsRepository(apiKey),
        budgetSettings = FakeBudgetSettingsRepository(budgetSettings),
        budgetDefaults = defaults,
        costLog = costLog,
        queues = ResourceQueues(),
        openAi = openAi,
        nowMillis = { 1_000L },
    ) to costLog

    @Test
    fun `without an api key it fails fast, without calling the network`() = runTest {
        val client = FakeOpenAiClient(AnalysisResult.Failed("no deberia llamarse"))
        val (runner, _) = runner(apiKey = null, openAi = client)

        val outcome = runner.run("texto", Depth.LOW, luna)

        assertEquals(GeneralAnalysisRunner.Outcome.NoApiKey, outcome)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `a model without pricing is refused before calling`() = runTest {
        val client = FakeOpenAiClient(AnalysisResult.Failed("no deberia llamarse"))
        val (runner, _) = runner(openAi = client)
        val mockModel = ModelOption("claude-sonnet", "Claude Sonnet")

        val outcome = runner.run("texto", Depth.LOW, mockModel)

        assertEquals(GeneralAnalysisRunner.Outcome.NoPricing, outcome)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `a successful call records its real cost in the log`() = runTest {
        val (runner, costLog) = runner()

        val outcome = runner.run("texto normalizado", Depth.LOW, luna) as GeneralAnalysisRunner.Outcome.Success

        assertEquals("analisis", outcome.text)
        val entry = costLog.recorded.single()
        assertEquals(ToolId.GENERAL, entry.tool)
        assertEquals(Depth.LOW, entry.depth)
        assertEquals(ResourceClass.LLM, entry.resourceClass)
        assertTrue(entry.succeeded)
        assertEquals(outcome.cost, entry.cost)
        // 100 tokens entrada * 0.10/1M + 50 tokens salida * 0.50/1M
        assertEquals(CostMicros.ofUsd(100 * 0.10 / 1_000_000.0 + 50 * 0.50 / 1_000_000.0), entry.cost)
    }

    @Test
    fun `a provider failure records nothing and reports the reason`() = runTest {
        val (runner, costLog) = runner(openAi = FakeOpenAiClient(AnalysisResult.Failed("HTTP 500")))

        val outcome = runner.run("texto", Depth.LOW, luna)

        assertEquals(GeneralAnalysisRunner.Outcome.Failed("HTTP 500"), outcome)
        assertTrue(costLog.recorded.isEmpty())
    }

    @Test
    fun `a tool already at its hard cap is denied before calling`() = runTest {
        val client = FakeOpenAiClient(AnalysisResult.Failed("no deberia llamarse"))
        val settings = BudgetSettings(toolLimits = mapOf(ToolId.GENERAL to CostMicros.ZERO))
        val (runner, _) = runner(budgetSettings = settings, openAi = client)

        val outcome = runner.run("texto", Depth.LOW, luna)

        assertEquals(GeneralAnalysisRunner.Outcome.Denied(DenyReason.TOOL_COST), outcome)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `a depth call count already exhausted is denied before calling`() = runTest {
        val client = FakeOpenAiClient(AnalysisResult.Failed("no deberia llamarse"))
        val settings = BudgetSettings(
            depthBudgets = mapOf(Depth.LOW to DepthBudget(costLimit = CostMicros.ofUsd(10.0), maxPaidCalls = 0)),
        )
        val (runner, _) = runner(budgetSettings = settings, openAi = client)

        val outcome = runner.run("texto", Depth.LOW, luna)

        assertEquals(GeneralAnalysisRunner.Outcome.Denied(DenyReason.DEPTH_CALLS), outcome)
        assertEquals(0, client.callCount)
    }
}
