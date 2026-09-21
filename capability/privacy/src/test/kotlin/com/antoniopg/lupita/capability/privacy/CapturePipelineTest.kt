package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.AppSignals
import com.antoniopg.lupita.core.model.CaptureFailure
import com.antoniopg.lupita.core.model.CaptureResult
import com.antoniopg.lupita.core.model.CatalogEntry
import com.antoniopg.lupita.core.model.DecisionSource
import com.antoniopg.lupita.core.model.FakeScreenSource
import com.antoniopg.lupita.core.model.ForegroundApp
import com.antoniopg.lupita.core.model.PixelBuffer
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyGroup
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.RawCapture
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.UiNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePipelineTest {

    private val catalog = PrivacyCatalog(
        groups = listOf(
            PrivacyGroup("banking", emptyMap(), PrivacyTier.PROTECTED),
            PrivacyGroup("social", emptyMap(), PrivacyTier.NORMAL),
        ),
        entries = listOf(
            CatalogEntry(AppMatch.Exact("com.bank.app"), "banking", setOf("GLOBAL")),
            CatalogEntry(AppMatch.Exact("com.social.app"), "social", setOf("GLOBAL")),
        ),
    )
    private val gate = PrivacyGate(catalog)
    private val settings = PrivacySettings()
    private val region = SelectionRect(100, 200, 500, 600)

    private fun node(
        text: String?,
        bounds: SelectionRect = SelectionRect(120, 220, 480, 260),
        password: Boolean = false,
        editable: Boolean = false,
        withheld: Boolean = false,
        children: List<UiNode> = emptyList(),
    ) = UiNode(bounds, text = text, isPassword = password, isEditable = editable, contentWithheld = withheld, children = children)

    private fun captured(pkg: String, vararg nodes: UiNode) = CaptureResult.Captured(
        RawCapture(pkg, region, nodes.toList(), PixelBuffer(1, 1, intArrayOf(0)), 0L),
    )

    private fun source(pkg: String, signals: AppSignals = AppSignals(), result: CaptureResult) =
        FakeScreenSource(ForegroundApp(pkg, "App", signals)) { result }

    private fun run(source: FakeScreenSource, s: PrivacySettings = settings) =
        runBlocking { CapturePipeline(source, gate).run(region, s) }

    private fun ready(o: CapturePipeline.Outcome) = (o as CapturePipeline.Outcome.Ready).bundle

    @Test
    fun `a protected app never reaches capture and only yields a header`() {
        val src = source("com.bank.app", result = captured("com.bank.app", node("saldo 1.234 EUR")))

        val bundle = ready(run(src))

        assertEquals(0, src.captureCalls)
        assertEquals(PrivacyTier.PROTECTED, bundle.header.tier)
        assertFalse(bundle.header.contentRead)
        assertNull(bundle.content)
        assertFalse(bundle.requiresPreview)
    }

    @Test
    fun `a secure window signalled up front is protected without capturing`() {
        val src = source("com.social.app", AppSignals(secureWindow = true), captured("com.social.app", node("x")))

        val bundle = ready(run(src))

        assertEquals(0, src.captureCalls)
        assertEquals(DecisionSource.SECURE_WINDOW, bundle.header.decisionSource)
    }

    @Test
    fun `a secure window discovered while capturing is treated as protected`() {
        val src = source("com.social.app", result = CaptureResult.Failed(CaptureFailure.SECURE_WINDOW))

        val bundle = ready(run(src))

        assertEquals(1, src.captureCalls)
        assertEquals(PrivacyTier.PROTECTED, bundle.header.tier)
        assertEquals(DecisionSource.SECURE_WINDOW, bundle.header.decisionSource)
        assertNull(bundle.content)
    }

    @Test
    fun `other capture failures are reported, not hidden`() {
        val src = source("com.social.app", result = CaptureResult.Failed(CaptureFailure.TOO_SOON, "intervalo"))

        val out = run(src) as CapturePipeline.Outcome.Failed

        assertEquals(CaptureFailure.TOO_SOON, out.reason)
    }

    @Test
    fun `no foreground app is a failure and nothing is captured`() {
        val src = FakeScreenSource(null)

        val out = run(src) as CapturePipeline.Outcome.Failed

        assertEquals(CaptureFailure.NO_FOREGROUND_APP, out.reason)
        assertEquals(0, src.captureCalls)
    }

    @Test
    fun `if the app changed between the decision and the capture everything is discarded`() {
        val src = source("com.social.app", result = captured("com.bank.app", node("saldo")))

        val out = run(src) as CapturePipeline.Outcome.Failed

        assertEquals(CaptureFailure.APP_CHANGED, out.reason)
    }

    @Test
    fun `password fields are dropped with their subtree and counted`() {
        val secret = node("hunter2", password = true, children = listOf(node("child")))
        val src = source("com.social.app", result = captured("com.social.app", node("Usuario", editable = true), secret))

        val bundle = ready(run(src))

        assertEquals(listOf("Usuario"), bundle.content!!.nodes.map { it.text })
        assertEquals(1, bundle.header.droppedPasswordFields)
        assertTrue(bundle.header.hasFormFields)
    }

    @Test
    fun `with the measure off password fields are kept`() {
        val off = PrivacySettings(measures = mapOf(SecurityMeasure.DROP_PASSWORD_FIELDS to false))
        val src = source("com.social.app", result = captured("com.social.app", node("hunter2", password = true)))

        val bundle = ready(run(src, off))

        assertEquals(1, bundle.content!!.nodes.size)
        assertEquals(0, bundle.header.droppedPasswordFields)
    }

    @Test
    fun `nodes outside the region are dropped even if the source returned them`() {
        val outside = node("fuera", bounds = SelectionRect(700, 900, 800, 950))
        val src = source("com.social.app", result = captured("com.social.app", node("dentro"), outside))

        val bundle = ready(run(src))

        assertEquals(listOf("dentro"), bundle.content!!.nodes.map { it.text })
    }

    @Test
    fun `patterns are redacted in text and content description and counted`() {
        val n = UiNode(SelectionRect(120, 220, 480, 260), text = "Escribe a ana@example.com", contentDescription = "ana@example.com")
        val src = source("com.social.app", result = captured("com.social.app", n))

        val bundle = ready(run(src))

        val kept = bundle.content!!.nodes.single()
        assertEquals("Escribe a [CORREO]", kept.text)
        assertEquals("[CORREO]", kept.contentDescription)
        assertEquals(2, bundle.header.redactions["[CORREO]"])
    }

    @Test
    fun `with redaction off the text is left as it is`() {
        val off = PrivacySettings(measures = mapOf(SecurityMeasure.REDACT_PATTERNS to false))
        val src = source("com.social.app", result = captured("com.social.app", node("ana@example.com")))

        val bundle = ready(run(src, off))

        assertEquals("ana@example.com", bundle.content!!.nodes.single().text)
        assertTrue(bundle.header.redactions.isEmpty())
    }

    @Test
    fun `withheld nodes are counted`() {
        val src = source("com.social.app", result = captured("com.social.app", node(null, withheld = true), node("ok")))

        assertEquals(1, ready(run(src)).header.withheldNodes)
    }

    @Test
    fun `an unknown app is sensitive and asks for a preview`() {
        val src = source("com.unknown.app", result = captured("com.unknown.app", node("hola")))

        val bundle = ready(run(src))

        assertEquals(PrivacyTier.SENSITIVE, bundle.header.tier)
        assertTrue(bundle.requiresPreview)
        assertNotNull(bundle.content)
    }

    @Test
    fun `a sensitive app does not ask for a preview when the measure is off`() {
        val off = PrivacySettings(measures = mapOf(SecurityMeasure.PREVIEW_BEFORE_SEND to false))
        val src = source("com.unknown.app", result = captured("com.unknown.app", node("hola")))

        assertFalse(ready(run(src, off)).requiresPreview)
    }

    @Test
    fun `a normal app does not ask for a preview`() {
        val src = source("com.social.app", result = captured("com.social.app", node("hola")))

        assertFalse(ready(run(src)).requiresPreview)
    }
}
