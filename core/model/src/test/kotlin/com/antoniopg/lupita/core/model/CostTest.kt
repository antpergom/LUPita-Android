package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostTest {

    @Test
    fun `micros and usd convert both ways without rounding surprises`() {
        assertEquals(1_500_000L, CostMicros.ofUsd(1.5).value)
        assertEquals(1.5, CostMicros.ofUsd(1.5).toUsd(), 0.0000001)
    }

    @Test
    fun `adding many small costs never loses a micro to floating point`() {
        var total = CostMicros.ZERO
        repeat(1_000_000) { total += CostMicros(1) }

        assertEquals(1_000_000L, total.value)
        assertEquals(1.0, total.toUsd(), 0.0)
    }

    @Test
    fun `costs compare like the numbers they represent`() {
        assertTrue(CostMicros.ofUsd(0.02) < CostMicros.ofUsd(0.10))
        assertTrue(CostMicros.ofUsd(5.0) > CostMicros.ZERO)
    }

    @Test
    fun `subtracting spend from a limit gives the remaining budget`() {
        val limit = CostMicros.ofUsd(1.0)
        val spent = CostMicros.ofUsd(0.35)

        assertEquals(CostMicros.ofUsd(0.65), limit - spent)
    }
}
