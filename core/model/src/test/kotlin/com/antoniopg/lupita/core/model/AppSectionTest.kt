package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSectionTest {

    @Test
    fun `keys travel in an Intent so they are stable`() {
        assertEquals(listOf("settings", "history"), AppSection.entries.map { it.key })
    }

    @Test
    fun `fromKey round-trips and tolerates null or unknown`() {
        AppSection.entries.forEach { assertEquals(it, AppSection.fromKey(it.key)) }
        assertNull(AppSection.fromKey(null))
        assertNull(AppSection.fromKey("nope"))
    }
}
