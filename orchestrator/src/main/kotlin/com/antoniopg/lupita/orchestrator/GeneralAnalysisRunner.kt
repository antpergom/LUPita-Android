package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.AiCredentialsRepository
import com.antoniopg.lupita.core.model.BudgetDefaults
import com.antoniopg.lupita.core.model.BudgetPeriodClock
import com.antoniopg.lupita.core.model.BudgetSettingsRepository
import com.antoniopg.lupita.core.model.CostLogEntry
import com.antoniopg.lupita.core.model.CostLogRepository
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.ResourceClass
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.core.model.periodSpent
import com.antoniopg.lupita.core.model.toolSpent
import com.antoniopg.lupita.source.openai.AnalysisRequest
import com.antoniopg.lupita.source.openai.AnalysisResult
import com.antoniopg.lupita.source.openai.GeneralAnalysisPromptV1
import com.antoniopg.lupita.source.openai.OpenAiClient
import com.antoniopg.lupita.source.openai.OpenAiCost
import com.antoniopg.lupita.source.openai.OpenAiRequestBuilder
import kotlinx.coroutines.flow.first

/**
 * Primer consumidor real de F4: une presupuesto, colas y log de coste alrededor de la herramienta
 * "Analisis general" (`ToolId.GENERAL`, F5). El resto de herramientas (Verificacion, Deteccion de
 * IA, Entidades) tendran su propio runner cuando lleguen (F6) - este no se generaliza a proposito
 * hasta que exista un segundo caso real que confirme la forma comun.
 *
 * El presupuesto se comprueba ANTES de llamar con una cota superior conservadora
 * ([OpenAiCost.worstCaseEstimate], acotada por `max_output_tokens`), nunca con el coste real (no se
 * conoce hasta que la API responde) - asi nunca se "cuela" una llamada por encima del tope.
 * `selectionSpent`/`selectionPaidCalls` son 0 siempre: hoy una seleccion equivale a una unica
 * llamada (solo esta herramienta esta cableada); cuando varias herramientas compartan una misma
 * captura habra que acumularlos de verdad entre llamadas de la misma seleccion.
 */
class GeneralAnalysisRunner(
    private val aiCredentials: AiCredentialsRepository,
    private val budgetSettings: BudgetSettingsRepository,
    private val budgetDefaults: BudgetDefaults,
    private val costLog: CostLogRepository,
    private val queues: ResourceQueues,
    private val openAi: OpenAiClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data class Success(val text: String, val cost: CostMicros) : Outcome
        data class Denied(val reason: DenyReason) : Outcome
        data class Failed(val reason: String) : Outcome
        data object NoApiKey : Outcome
        data object NoPricing : Outcome
    }

    suspend fun run(text: String, depth: Depth, model: ModelOption): Outcome {
        val apiKey = aiCredentials.credentials.first()?.apiKey ?: return Outcome.NoApiKey

        val maxOutputTokens = OpenAiRequestBuilder.maxOutputTokens(depth)
        val worstCase = OpenAiCost.worstCaseEstimate(
            promptChars = GeneralAnalysisPromptV1.system.length,
            textChars = text.length,
            maxOutputTokens = maxOutputTokens,
            model = model,
        ) ?: return Outcome.NoPricing

        val settings = budgetSettings.settings.first()
        val depthBudget = settings.depthBudget(depth, budgetDefaults)
        val toolLimit = settings.toolLimit(ToolId.GENERAL, budgetDefaults)
        val globalLimit = settings.effectiveGlobalLimit(budgetDefaults)
        val periodStart = BudgetPeriodClock.periodStartMillis(settings.globalPeriod, nowMillis())
        val state = BudgetState(
            selectionSpent = CostMicros.ZERO,
            selectionPaidCalls = 0,
            toolSpent = costLog.toolSpent(ToolId.GENERAL, periodStart),
            periodSpent = costLog.periodSpent(periodStart),
        )

        val decision = BudgetEnforcer.evaluate(worstCase, depthBudget, toolLimit, globalLimit, state)
        if (decision is BudgetDecision.Denied) return Outcome.Denied(decision.reason)

        val result = queues.run(ResourceClass.LLM) {
            openAi.analyze(AnalysisRequest(text = text, depth = depth), apiKey, model.id)
        }

        return when (result) {
            is AnalysisResult.Failed -> Outcome.Failed(result.reason)
            is AnalysisResult.Success -> {
                // Ya se verifico que el modelo tiene precio (worstCase != null arriba): no debería ser
                // nulo aqui, pero si el precio cambiara a mitad de vuelo no se factura a ciegas.
                val cost = OpenAiCost.of(result, model) ?: return Outcome.NoPricing
                costLog.record(
                    CostLogEntry(
                        timestampMillis = nowMillis(),
                        tool = ToolId.GENERAL,
                        depth = depth,
                        capability = GeneralAnalysisPromptV1.CAPABILITY_ID,
                        resourceClass = ResourceClass.LLM,
                        cost = cost,
                        succeeded = true,
                        model = result.model,
                    ),
                )
                Outcome.Success(result.text, cost)
            }
        }
    }
}
