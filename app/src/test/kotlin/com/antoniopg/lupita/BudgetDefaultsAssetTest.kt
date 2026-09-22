package com.antoniopg.lupita

import com.antoniopg.lupita.core.model.BudgetDefaults
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ToolId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de calidad del fichero de DATOS que se empaqueta: como `BudgetDefaults.parse` cae al fallback en
 * silencio ante cualquier dato que falte, un error tipografico dejaria un tope sin afinar sin que nadie lo note.
 */
class BudgetDefaultsAssetTest {

    private val raw = File("src/main/assets/budget_defaults.json").readText()
    private val defaults = BudgetDefaults.parse(raw)

    @Test
    fun `the packaged file parses and covers every depth and tool`() {
        Depth.entries.forEach { depth ->
            assertTrue("sin tope de coste para $depth", defaults.depthBudget(depth).costLimit.value > 0)
            assertTrue("sin tope de llamadas para $depth", defaults.depthBudget(depth).maxPaidCalls > 0)
        }
        ToolId.entries.forEach { tool -> assertTrue("sin tope para $tool", defaults.toolLimit(tool).value > 0) }
    }

    @Test
    fun `the three depths are not all set to the same limit`() {
        val limits = Depth.entries.map { defaults.depthBudget(it).costLimit }
        assertEquals(3, limits.distinct().size)
        assertTrue(defaults.depthBudget(Depth.LOW).costLimit < defaults.depthBudget(Depth.HIGH).costLimit)
    }

}
