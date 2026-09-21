package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.CaptureFailure
import com.antoniopg.lupita.core.model.CaptureRequest
import com.antoniopg.lupita.core.model.CaptureResult
import com.antoniopg.lupita.core.model.CapturedContent
import com.antoniopg.lupita.core.model.ContextBundle
import com.antoniopg.lupita.core.model.ContextHeader
import com.antoniopg.lupita.core.model.DecisionSource
import com.antoniopg.lupita.core.model.ForegroundApp
import com.antoniopg.lupita.core.model.PrivacyDecision
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.RawCapture
import com.antoniopg.lupita.core.model.ScreenSource
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.UiNode
import com.antoniopg.lupita.core.model.intersects

/**
 * El unico camino para capturar: decide la privacidad ANTES de leer nada y solo entonces pide la region a
 * la [ScreenSource]. El orquestador no puede saltarselo porque no tiene otra forma de obtener contexto.
 *
 * 1. Lee solo la app de primer plano y la decide con [PrivacyGate].
 * 2. Protegida -> devuelve solo la cabecera; **no se llama a `capture`**.
 * 3. Si no, captura la region. Si la fuente descubre una ventana protegida, se trata como protegida.
 * 4. Si la app cambio entre la decision y la captura, se descarta todo.
 * 5. Quita los campos de contrasena y lo que cae fuera de la region, redacta el texto y cuenta lo hecho.
 */
class CapturePipeline(private val source: ScreenSource, private val gate: PrivacyGate) {

    sealed interface Outcome {
        class Ready(val bundle: ContextBundle) : Outcome
        data class Failed(val reason: CaptureFailure, val detail: String? = null) : Outcome
    }

    suspend fun run(region: SelectionRect, settings: PrivacySettings): Outcome {
        if (!source.isAvailable) return Outcome.Failed(CaptureFailure.SERVICE_UNAVAILABLE)
        val app = source.foreground() ?: return Outcome.Failed(CaptureFailure.NO_FOREGROUND_APP)
        val decision = gate.decide(app.packageName, app.signals, settings)
        if (decision.tier == PrivacyTier.PROTECTED) return Outcome.Ready(headerOnly(app, decision))

        return when (val result = source.capture(CaptureRequest(region))) {
            is CaptureResult.Failed ->
                if (result.reason == CaptureFailure.SECURE_WINDOW) {
                    Outcome.Ready(headerOnly(app, PrivacyDecision(PrivacyTier.PROTECTED, DecisionSource.SECURE_WINDOW)))
                } else {
                    Outcome.Failed(result.reason, result.detail)
                }

            is CaptureResult.Captured ->
                if (result.capture.packageName != app.packageName) {
                    Outcome.Failed(CaptureFailure.APP_CHANGED)
                } else {
                    Outcome.Ready(assemble(app, decision, result.capture, region, settings))
                }
        }
    }

    private fun headerOnly(app: ForegroundApp, decision: PrivacyDecision) = ContextBundle(
        header = ContextHeader(
            packageName = app.packageName,
            appLabel = app.appLabel,
            tier = PrivacyTier.PROTECTED,
            decisionSource = decision.source,
            group = decision.group,
            contentRead = false,
        ),
        content = null,
        requiresPreview = false,
    )

    private fun assemble(
        app: ForegroundApp,
        decision: PrivacyDecision,
        raw: RawCapture,
        region: SelectionRect,
        settings: PrivacySettings,
    ): ContextBundle {
        val dropPasswords = settings.isEnabled(SecurityMeasure.DROP_PASSWORD_FIELDS)
        val redact = settings.isEnabled(SecurityMeasure.REDACT_PATTERNS)
        val stats = Stats()
        val nodes = raw.nodes.mapNotNull { prune(it, region, dropPasswords, redact, stats) }

        return ContextBundle(
            header = ContextHeader(
                packageName = app.packageName,
                appLabel = app.appLabel,
                tier = decision.tier,
                decisionSource = decision.source,
                group = decision.group,
                contentRead = true,
                hasFormFields = stats.formFields,
                withheldNodes = stats.withheld,
                droppedPasswordFields = stats.passwords,
                redactions = stats.redactions.mapKeys { it.key.token },
                suggestion = decision.suggestion,
            ),
            content = CapturedContent(region, nodes, raw.pixels, raw.provenance),
            requiresPreview = decision.tier == PrivacyTier.SENSITIVE &&
                settings.isEnabled(SecurityMeasure.PREVIEW_BEFORE_SEND),
        )
    }

    private class Stats {
        var formFields = false
        var withheld = 0
        var passwords = 0
        val redactions = mutableMapOf<Redactor.Kind, Int>()
    }

    /** `null` si el nodo (y todo lo que cuelga de el) no debe salir. */
    private fun prune(node: UiNode, region: SelectionRect, dropPasswords: Boolean, redact: Boolean, stats: Stats): UiNode? {
        if (!node.bounds.intersects(region)) return null
        if (node.isEditable || node.isPassword) stats.formFields = true
        if (node.isPassword && dropPasswords) {
            stats.passwords++
            return null
        }
        if (node.contentWithheld) stats.withheld++
        return node.copy(
            text = node.text?.let { clean(it, redact, stats) },
            contentDescription = node.contentDescription?.let { clean(it, redact, stats) },
            children = node.children.mapNotNull { prune(it, region, dropPasswords, redact, stats) },
        )
    }

    private fun clean(text: String, redact: Boolean, stats: Stats): String {
        if (!redact) return text
        val result = Redactor.redact(text)
        result.counts.forEach { (kind, n) -> stats.redactions.merge(kind, n, Int::plus) }
        return result.text
    }
}
