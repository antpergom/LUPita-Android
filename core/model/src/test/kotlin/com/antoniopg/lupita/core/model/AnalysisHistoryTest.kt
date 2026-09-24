package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisHistoryTest {
    private fun entry(sessionId: String, tool: ToolId, timestamp: Long, appLabel: String? = "X") = AnalysisHistoryEntry(
        sessionId = sessionId,
        timestampMillis = timestamp,
        packageName = "com.twitter.android",
        appLabel = appLabel,
        tool = tool,
        capability = "c",
        model = "gpt-6-luna",
        succeeded = true,
        text = "texto",
        reason = null,
        cost = CostMicros.ofUsd(0.0001),
    )

    @Test
    fun `entries from the same session become a single session with every tool`() {
        val entries = listOf(
            entry("s1", ToolId.GENERAL, 100),
            entry("s1", ToolId.VERIFY, 105),
        )

        val sessions = entries.groupedBySession()

        assertEquals(1, sessions.size)
        assertEquals(listOf(ToolId.GENERAL, ToolId.VERIFY), sessions.single().tools)
    }

    @Test
    fun `tools within a session are ordered by ToolId, not insertion order`() {
        val entries = listOf(
            entry("s1", ToolId.ENTITY, 100),
            entry("s1", ToolId.GENERAL, 105),
        )

        val tools = entries.groupedBySession().single().tools

        assertEquals(listOf(ToolId.GENERAL, ToolId.ENTITY), tools)
    }

    @Test
    fun `a session's timestamp is its newest entry, not its first`() {
        val entries = listOf(
            entry("s1", ToolId.GENERAL, 100),
            entry("s1", ToolId.VERIFY, 500),
        )

        assertEquals(500L, entries.groupedBySession().single().timestampMillis)
    }

    @Test
    fun `sessions are ordered newest first`() {
        val entries = listOf(
            entry("old", ToolId.GENERAL, 100),
            entry("new", ToolId.GENERAL, 900),
        )

        val ids = entries.groupedBySession().map { it.sessionId }

        assertEquals(listOf("new", "old"), ids)
    }

    @Test
    fun `an empty list groups into no sessions`() {
        assertEquals(emptyList<AnalysisSession>(), emptyList<AnalysisHistoryEntry>().groupedBySession())
    }
}
