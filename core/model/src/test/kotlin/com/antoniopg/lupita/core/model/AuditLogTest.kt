package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuditLogTest {

    @Test
    fun `a pending suggestion survives an encode-decode round trip`() {
        val s = PendingSuggestion("com.mycompany.bankapp", "banking", PrivacyTier.PROTECTED)

        assertEquals(s, PendingSuggestion.decode(s.encode()))
    }

    @Test
    fun `malformed or unknown-tier suggestions decode to null`() {
        assertNull(PendingSuggestion.decode("garbage"))
        assertNull(PendingSuggestion.decode("com.x|banking|not-a-tier"))
        assertNull(PendingSuggestion.decode("|banking|protected")) // paquete vacio
    }

    @Test
    fun `an audit entry survives an encode-decode round trip`() {
        val e = AuditEntry(1_700_000_000_000, "com.social.app", PrivacyTier.SENSITIVE, AuditOutcome.READ, 42)

        assertEquals(e, AuditEntry.decode(e.encode()))
    }

    @Test
    fun `malformed audit entries decode to null`() {
        assertNull(AuditEntry.decode("garbage"))
        assertNull(AuditEntry.decode("notanumber|com.x|sensitive|read|1"))
        assertNull(AuditEntry.decode("1|com.x|whatever|read|1"))
        assertNull(AuditEntry.decode("1|com.x|sensitive|whatever|1"))
    }

    @Test
    fun `outcome keys round trip and an unknown key is null`() {
        AuditOutcome.entries.forEach { assertEquals(it, AuditOutcome.fromKey(it.key)) }
        assertNull(AuditOutcome.fromKey("nope"))
    }
}
