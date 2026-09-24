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
import com.antoniopg.lupita.source.openai.OpenAiClient
import com.antoniopg.lupita.source.openai.OpenAiCost
import com.antoniopg.lupita.source.openai.OpenAiRequestBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Consumidor real de F4: une presupuesto, colas y log de coste alrededor de cualquiera de las
 * cuatro herramientas (F5: Analisis general; F6: Verificacion, Deteccion de IA, Investigacion de
 * entidades) — generalizado en F6 a partir del `GeneralAnalysisRunner` original de F5 en cuanto
 * hubo un segundo caso real que confirmara la forma comun (decision explicita de F5: no
 * generalizar antes de tiempo).
 *
 * El presupuesto se comprueba ANTES de llamar con una cota superior conservadora
 * ([OpenAiCost.worstCaseEstimate], acotada por `max_output_tokens`), nunca con el coste real (no se
 * conoce hasta que la API responde) - asi nunca se "cuela" una llamada por encima del tope.
 *
 * [selectionSpent]/[selectionPaidCalls] (parametros de [run], F6): lo YA gastado/llamado en esta
 * misma seleccion (una captura) por las herramientas ejecutadas antes que esta — el llamador
 * (`OverlayService`) los acumula entre llamadas sucesivas cuando varias herramientas comparten una
 * captura (arquitectura F1: "cuatro burbujas en alta reparten, no multiplican" el presupuesto).
 */
class AnalysisRunner(
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

    suspend fun run(
        tool: ToolId,
        capabilityId: String,
        systemPrompt: String,
        text: String,
        depth: Depth,
        model: ModelOption,
        selectionSpent: CostMicros = CostMicros.ZERO,
        selectionPaidCalls: Int = 0,
    ): Outcome {
        val apiKey = aiCredentials.credentials.first()?.apiKey ?: return Outcome.NoApiKey

        val maxOutputTokens = OpenAiRequestBuilder.maxOutputTokens(depth)
        val worstCase = OpenAiCost.worstCaseEstimate(
            promptChars = systemPrompt.length,
            textChars = text.length,
            maxOutputTokens = maxOutputTokens,
            model = model,
        ) ?: return Outcome.NoPricing

        val settings = budgetSettings.settings.first()
        val depthBudget = settings.depthBudget(depth, budgetDefaults)
        val toolLimit = settings.toolLimit(tool, budgetDefaults)
        val globalLimit = settings.effectiveGlobalLimit(budgetDefaults)
        val periodStart = BudgetPeriodClock.periodStartMillis(settings.globalPeriod, nowMillis())
        val state = BudgetState(
            selectionSpent = selectionSpent,
            selectionPaidCalls = selectionPaidCalls,
            toolSpent = costLog.toolSpent(tool, periodStart),
            periodSpent = costLog.periodSpent(periodStart),
        )

        val decision = BudgetEnforcer.evaluate(worstCase, depthBudget, toolLimit, globalLimit, state)
        if (decision is BudgetDecision.Denied) return Outcome.Denied(decision.reason)

        // El presupuesto se comprobo UNA vez arriba: los reintentos son del mismo intento ya
        // permitido (nada se ha gastado todavia, ni aunque fallen), no piden permiso otra vez.
        val result = retryTransient {
            queues.run(ResourceClass.LLM) {
                openAi.analyze(AnalysisRequest(text = text, depth = depth), apiKey, model.id)
            }
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
                        tool = tool,
                        depth = depth,
                        capability = capabilityId,
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

    /**
     * Reintenta solo [AnalysisResult.Failed] con `transient = true` (429/5xx/fallo de red — ver
     * `HttpOpenAiClient`), nunca un rechazo permanente (clave invalida, request mal formada), que
     * fallaria otra vez identico. `MAX_ATTEMPTS = 2` (un solo reintento) es deliberadamente
     * conservador: esto ya tiene su presupuesto verificado, pero no hay razon para insistir mas de
     * una vez ante algo que quiza no sea pasajero de verdad.
     */
    private suspend fun retryTransient(block: suspend () -> AnalysisResult): AnalysisResult {
        var attempt = 1
        var result = block()
        while (result is AnalysisResult.Failed && result.transient && attempt < MAX_ATTEMPTS) {
            delay(RETRY_DELAY_MS)
            result = block()
            attempt++
        }
        return result
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 1_000L
    }
}
