package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepthTest {

    @Test
    fun `default depth is low`() {
        assertEquals(Depth.LOW, Depth.DEFAULT)
    }

    @Test
    fun `persisted keys are stable`() {
        assertEquals(listOf("low", "medium", "high"), Depth.entries.map { it.key })
    }

    @Test
    fun `fromKey round-trips every level`() {
        Depth.entries.forEach { assertEquals(it, Depth.fromKey(it.key)) }
    }

    @Test
    fun `fromKey returns null for an unknown key`() {
        assertNull(Depth.fromKey("extreme"))
    }
}
