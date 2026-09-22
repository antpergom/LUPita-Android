package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.DepthBudget
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetEnforcerTest {
    private val depthBudget = DepthBudget(costLimit = CostMicros.ofUsd(0.10), maxPaidCalls = 6)
    private val toolLimit = CostMicros.ofUsd(1.0)
    private val globalLimit = CostMicros.ofUsd(20.0)
    private val zeroState = BudgetState(
        selectionSpent = CostMicros.ZERO,
        selectionPaidCalls = 0,
        toolSpent = CostMicros.ZERO,
        periodSpent = CostMicros.ZERO,
    )

    @Test
    fun `allows a call that fits comfortably in every cap`() {
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = zeroState,
        )
        assertEquals(BudgetDecision.Allowed, decision)
    }

    @Test
    fun `allows a call that lands exactly on a cap boundary`() {
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.10),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = zeroState,
        )
        assertEquals(BudgetDecision.Allowed, decision)
    }

    @Test
    fun `denies for depth cost when the selection already spent close to the depth limit`() {
        val state = zeroState.copy(selectionSpent = CostMicros.ofUsd(0.095))
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = state,
        )
        assertEquals(BudgetDecision.Denied(DenyReason.DEPTH_COST), decision)
    }

    @Test
    fun `denies for depth calls when the selection already used every paid call`() {
        val state = zeroState.copy(selectionPaidCalls = 6)
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = state,
        )
        assertEquals(BudgetDecision.Denied(DenyReason.DEPTH_CALLS), decision)
    }

    @Test
    fun `denies for tool cost when that tool is already near its own hard cap`() {
        val state = zeroState.copy(toolSpent = CostMicros.ofUsd(0.995))
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = state,
        )
        assertEquals(BudgetDecision.Denied(DenyReason.TOOL_COST), decision)
    }

    @Test
    fun `denies for global cost when the period already spent close to the global limit`() {
        val state = zeroState.copy(periodSpent = CostMicros.ofUsd(19.995))
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = state,
        )
        assertEquals(BudgetDecision.Denied(DenyReason.GLOBAL_COST), decision)
    }

    @Test
    fun `checks depth cost before depth calls when both would fail`() {
        val state = zeroState.copy(selectionSpent = CostMicros.ofUsd(0.095), selectionPaidCalls = 6)
        val decision = BudgetEnforcer.evaluate(
            requested = CostMicros.ofUsd(0.01),
            depthBudget = depthBudget,
            toolLimit = toolLimit,
            globalLimit = globalLimit,
            state = state,
        )
        assertEquals(BudgetDecision.Denied(DenyReason.DEPTH_COST), decision)
    }
}
