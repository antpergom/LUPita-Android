package com.antoniopg.lupita.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DepthDefaultEntry(val depth: String, val costLimitUsd: Double, val maxPaidCalls: Int)

@Serializable
private data class ToolDefaultEntry(val tool: String, val limitUsd: Double)

@Serializable
private data class BudgetDefaultsFile(
    val depths: List<DepthDefaultEntry> = emptyList(),
    val tools: List<ToolDefaultEntry> = emptyList(),
    val globalLimitUsd: Double? = null,
    val globalPeriod: String? = null,
)

/**
 * Valores de fabrica del presupuesto: DATOS (`assets/budget_defaults.json`), no constantes en Kotlin, para
 * poder variarlos por usuario o region en el futuro (decision 2026-09-22) sin tocar codigo. Un dato que
 * falta o esta roto cae a [FALLBACK] entrada por entrada — nunca una excepcion, y nunca un limite sin tope.
 */
data class BudgetDefaults(
    private val depths: Map<Depth, DepthBudget>,
    private val tools: Map<ToolId, CostMicros>,
    val globalLimit: CostMicros,
    val globalPeriod: BudgetPeriod,
) {
    fun depthBudget(depth: Depth): DepthBudget = depths[depth] ?: FALLBACK.depths.getValue(depth)

    fun toolLimit(tool: ToolId): CostMicros = tools[tool] ?: FALLBACK.tools.getValue(tool)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Cifras de partida, deliberadamente sin afinar («alto por el momento», decision 2026-09-22): sirven
         * para no bloquear nada mientras no hay ninguna llamada de pago todavia (F5). Ajustar cuando haya
         * datos reales de gasto.
         *
         * `maxPaidCalls` de las 3 profundidades cubre siempre las 4 herramientas (decision 2026-09-25: las
         * tres profundidades deben poder llamar a las 4 tarjetas — el limite real llegara mas adelante
         * sobre el contexto u otros parametros, no sobre un tope de llamadas mas bajo que el numero de
         * herramientas). Verificado en el Pixel el 2026-09-25 que con `maxPaidCalls = 2` en LOW, la 3a y 4a
         * herramienta de una misma captura se denegaban SIEMPRE, no solo en el caso raro de gasto alto.
         */
        val FALLBACK = BudgetDefaults(
            depths = mapOf(
                Depth.LOW to DepthBudget(CostMicros.ofUsd(0.02), maxPaidCalls = ToolId.entries.size),
                Depth.MEDIUM to DepthBudget(CostMicros.ofUsd(0.10), maxPaidCalls = 6),
                Depth.HIGH to DepthBudget(CostMicros.ofUsd(0.50), maxPaidCalls = 20),
            ),
            tools = ToolId.entries.associateWith { CostMicros.ofUsd(1.0) },
            globalLimit = CostMicros.ofUsd(20.0),
            globalPeriod = BudgetPeriod.MONTHLY,
        )

        fun parse(text: String): BudgetDefaults = runCatching {
            val file = json.decodeFromString<BudgetDefaultsFile>(text)
            val depths = file.depths.mapNotNull { e ->
                Depth.fromKey(e.depth)?.let { it to DepthBudget(CostMicros.ofUsd(e.costLimitUsd), e.maxPaidCalls) }
            }.toMap()
            val tools = file.tools.mapNotNull { e ->
                ToolId.fromKey(e.tool)?.let { it to CostMicros.ofUsd(e.limitUsd) }
            }.toMap()
            BudgetDefaults(
                depths = Depth.entries.associateWith { depths[it] ?: FALLBACK.depthBudget(it) },
                tools = ToolId.entries.associateWith { tools[it] ?: FALLBACK.toolLimit(it) },
                globalLimit = file.globalLimitUsd?.let(CostMicros::ofUsd) ?: FALLBACK.globalLimit,
                globalPeriod = BudgetPeriod.fromKey(file.globalPeriod) ?: FALLBACK.globalPeriod,
            )
        }.getOrDefault(FALLBACK)
    }
}
