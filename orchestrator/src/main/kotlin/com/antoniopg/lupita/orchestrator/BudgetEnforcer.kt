package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.DepthBudget

/** Lo gastado hasta ahora en cada uno de los tres topes, justo antes de la llamada que se esta evaluando. */
data class BudgetState(
    val selectionSpent: CostMicros,
    val selectionPaidCalls: Int,
    val toolSpent: CostMicros,
    val periodSpent: CostMicros,
)

enum class DenyReason { DEPTH_COST, DEPTH_CALLS, TOOL_COST, GLOBAL_COST }

sealed interface BudgetDecision {
    data object Allowed : BudgetDecision
    data class Denied(val reason: DenyReason) : BudgetDecision
}

/**
 * Los tres topes duros (decision 2026-09-22), en orden: primero el de la profundidad (coste Y numero de
 * llamadas), despues el de la herramienta, despues el global. Puro: solo numeros, sin tocar Room ni un
 * proveedor — asi se prueba sin base de datos ni red.
 */
object BudgetEnforcer {
    fun evaluate(
        requested: CostMicros,
        depthBudget: DepthBudget,
        toolLimit: CostMicros,
        globalLimit: CostMicros,
        state: BudgetState,
    ): BudgetDecision = when {
        state.selectionSpent + requested > depthBudget.costLimit -> BudgetDecision.Denied(DenyReason.DEPTH_COST)
        state.selectionPaidCalls + 1 > depthBudget.maxPaidCalls -> BudgetDecision.Denied(DenyReason.DEPTH_CALLS)
        state.toolSpent + requested > toolLimit -> BudgetDecision.Denied(DenyReason.TOOL_COST)
        state.periodSpent + requested > globalLimit -> BudgetDecision.Denied(DenyReason.GLOBAL_COST)
        else -> BudgetDecision.Allowed
    }
}
