package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolIdTest {

    @Test
    fun `persisted keys are stable - renaming one would orphan saved user settings`() {
        // Si este test falla, NO se actualiza el valor esperado sin migrar antes lo ya guardado.
        assertEquals(
            listOf("general", "verify", "ai_detect", "entity"),
            ToolId.entries.map { it.key },
        )
    }

    @Test
    fun `keys are unique and not blank`() {
        val keys = ToolId.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.isNotBlank() })
    }

    @Test
    fun `fromKey round-trips every tool`() {
        ToolId.entries.forEach { assertEquals(it, ToolId.fromKey(it.key)) }
    }

    @Test
    fun `fromKey returns null for an unknown or empty key`() {
        assertNull(ToolId.fromKey("nope"))
        assertNull(ToolId.fromKey(""))
    }
}
