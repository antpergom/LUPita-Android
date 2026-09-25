package com.antoniopg.lupita.overlay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.antoniopg.lupita.LupitaApp
import com.antoniopg.lupita.MainActivity
import com.antoniopg.lupita.R
import com.antoniopg.lupita.capability.context.ContextNormalizer
import com.antoniopg.lupita.capability.image.C2paDetection
import com.antoniopg.lupita.capability.image.C2paDetector
import com.antoniopg.lupita.capability.image.asPromptContext
import com.antoniopg.lupita.capability.ocr.OcrGate
import com.antoniopg.lupita.capability.ocr.TextOcr
import com.antoniopg.lupita.capability.privacy.CapturePipeline
import com.antoniopg.lupita.capability.screen.AccessibilityScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionSession
import com.antoniopg.lupita.capability.screen.RegionImage
import com.antoniopg.lupita.capability.text.EntityExtractor
import com.antoniopg.lupita.capability.text.ExtractedEntities
import com.antoniopg.lupita.capability.text.asPromptContext
import com.antoniopg.lupita.capability.web.FetchResult
import com.antoniopg.lupita.capability.web.Readability
import com.antoniopg.lupita.capability.web.asPromptContext
import com.antoniopg.lupita.capture.ProjectionConsentActivity
import com.antoniopg.lupita.core.model.AnalysisHistoryEntry
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.ArtifactHash
import com.antoniopg.lupita.core.model.AuditEntry
import com.antoniopg.lupita.core.model.AuditOutcome
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.ContentPattern
import com.antoniopg.lupita.core.model.ContextBundle
import com.antoniopg.lupita.core.model.ContextHeader
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ModelCatalog
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.core.model.PendingSuggestion
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.EncodedImage
import com.antoniopg.lupita.core.model.ImageSavePolicy
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.ScreenSource
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.orchestrator.AnalysisRunner
import com.antoniopg.lupita.orchestrator.DenyReason
import com.antoniopg.lupita.source.openai.AiDetectPromptV3
import com.antoniopg.lupita.source.openai.EntityPromptV3
import com.antoniopg.lupita.source.openai.GeneralAnalysisPromptV2
import com.antoniopg.lupita.source.openai.VerifyPromptV3
import com.antoniopg.lupita.ui.overlay.AskSaveOverlay
import com.antoniopg.lupita.ui.overlay.BubbleOverlay
import com.antoniopg.lupita.ui.overlay.ToolResultState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano que sostiene la burbuja. Su notificacion es la UNICA via de apagarla
 * (accion "Apagar"): no hay gesto de descarte.
 *
 * Dibuja la burbuja (`BubbleOverlay`, en :ui:overlay) mientras el servicio esta vivo.
 *
 * `START_NOT_STICKY` a proposito: con targetSdk >= 35 y SYSTEM_ALERT_WINDOW solo se puede arrancar un
 * servicio en primer plano desde segundo plano si ya hay una ventana de overlay visible, asi que no
 * se puede confiar en que Android lo resucite tras matar el proceso. Se arranca siempre desde la
 * Activity (visible, permitido): abrir la app siempre arranca la burbuja.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bubble: BubbleOverlay? = null
    private var pendingRect: SelectionRect? = null
    /** La ultima captura con el panel de resultados abierto — la necesita [retryAnalysis]. */
    private var resultsContext: ResultsContext? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ProjectionSession.onReady = null
        bubble?.remove()
        bubble = null
        scope.cancel()
        super.onDestroy()
    }

    private fun showBubbleIfNeeded() {
        // `start()` se llama cada vez que se abre la app: no duplicar la ventana.
        if (bubble != null) return
        // Sin el permiso, addView lanza BadTokenException; la Activity ya lo exige antes de arrancar.
        if (!Settings.canDrawOverlays(this)) return
        val settings = (application as LupitaApp).container.settings
        bubble = BubbleOverlay(
            this, settings, scope,
            onOpenApp = ::openApp, onCapture = ::onCapture, onQuit = ::quit, onRetryResult = ::retryAnalysis,
        ).also { it.show() }
    }

    /**
     * F1.3: captura la region con la puerta de privacidad de por medio y ensena un resumen SIN contenido.
     * El analisis llega en F5; de momento solo se comprueba que la captura funciona.
     */
    private fun onCapture(rect: SelectionRect) {
        scope.launch {
            // La capa de captura se acaba de quitar: esperar unos fotogramas para que no salga en la imagen.
            delay(CAPTURE_SETTLE_MS)
            val accessibility = AccessibilityScreenSource(this@OverlayService)
            when {
                accessibility.isAvailable -> runCapture(accessibility, rect, CAPTURE_SETTLE_MS)
                // Respaldo sin accesibilidad: solo pixeles, y hace falta el permiso de proyeccion (por sesion).
                ProjectionSession.isActive -> runCapture(ProjectionScreenSource(), rect, CAPTURE_SETTLE_MS)
                else -> requestProjection(rect)
            }
        }
    }

    /** Pide el permiso de proyeccion; al concederse, se captura la misma region (guardada mientras tanto). */
    private fun requestProjection(rect: SelectionRect) {
        pendingRect = rect
        ProjectionSession.onReady = {
            val region = pendingRect
            pendingRect = null
            ProjectionSession.onReady = null
            // El dialogo del sistema acaba de cerrarse: esperar a que deje de estar en pantalla.
            if (region != null) {
                scope.launch {
                    delay(PROJECTION_SETTLE_MS)
                    runCapture(ProjectionScreenSource(), region, CAPTURE_SETTLE_MS + PROJECTION_SETTLE_MS)
                }
            }
        }
        startActivity(Intent(this, ProjectionConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun runCapture(source: ScreenSource, rect: SelectionRect, settleMs: Long) {
        val container = (application as LupitaApp).container
        val settings = container.privacySettings.settings.first()
        val bubbleSettings = container.settings.settings.first()
        val modelId = container.settings.modelId.first()
        val started = SystemClock.elapsedRealtime()
        // La burbuja no debe salir en su propia captura (bug real hallado verificando F5/F6 en el
        // Pixel, 2026-09-25): se oculta solo durante la lectura de pixeles, nunca mientras se
        // analiza (eso puede tardar varios segundos y dejaria al usuario sin burbuja visible).
        bubble?.setBubbleVisible(false)
        val outcome = try {
            withContext(Dispatchers.Default) {
                CapturePipeline(source, container.privacyGate).run(rect, settings)
            }
        } finally {
            bubble?.setBubbleVisible(true)
        }
        recordSuggestion(outcome)
        val summary = summarize(
            outcome,
            SystemClock.elapsedRealtime() - started,
            settleMs,
            settings.imageSavePolicy,
            settings.fixtureRecorderEnabled,
            settings.isEnabled(SecurityMeasure.AUDIT_LOG),
            bubbleSettings,
            modelId,
        )
        // Varios toasts, no un `\n`: en el Pixel un solo texto de dos lineas se queda corto, la primera
        // linea ya llena el hueco visible y la segunda nunca llega a verse (hallado verificando F2/F3).
        toast(summary.message)
        summary.normalized?.let { delay(TOAST_GAP_MS); toast(it) }
        // F5/F6: los resultados de las herramientas van al panel en vivo de la burbuja (BubbleOverlay.
        // showResults/updateResult), no a un toast/notificacion - solo si ni siquiera pudo arrancar
        // (sin modelo configurado) hay algo que avisar aqui.
        summary.analysisError?.let { delay(TOAST_GAP_MS); toast(it) }
    }

    private class Summary(val message: String, val normalized: String? = null, val analysisError: String? = null)

    /** La propuesta por nombre (si la hay) se registra siempre: no depende de la medida de auditoria. */
    private suspend fun recordSuggestion(outcome: CapturePipeline.Outcome) {
        if (outcome !is CapturePipeline.Outcome.Ready) return
        val header = outcome.bundle.header
        val suggestion = header.suggestion ?: return
        (application as LupitaApp).container.privacySettings
            .recordSuggestion(PendingSuggestion(header.packageName, suggestion.group, suggestion.tier))
    }

    /** Solo metadatos (hora, app, nivel, resultado, tamano): ver docs/SEGURIDAD.md. */
    private suspend fun appendAudit(header: ContextHeader, kilobytes: Int) {
        (application as LupitaApp).container.privacySettings.appendAuditEntry(
            AuditEntry(
                timestampMillis = System.currentTimeMillis(),
                packageName = header.packageName,
                tier = header.tier,
                outcome = if (header.contentRead) AuditOutcome.READ else AuditOutcome.PROTECTED,
                kilobytes = kilobytes,
            ),
        )
    }

    /**
     * F5 paso 3: primer consumidor real de F4 (BudgetEnforcer/ResourceQueues/CostLogRepository).
     * Nunca lanza — cada desenlace de [GeneralAnalysisRunner.Outcome] tiene su propio mensaje.
     */
    /**
     * F6: recorre las herramientas activas en la burbuja en un orden fijo (no el de insercion del
     * `Set`, para que el orden de los toasts sea siempre el mismo) y acumula de verdad lo gastado en
     * esta seleccion entre una herramienta y la siguiente — arquitectura F1: "cuatro burbujas en
     * alta reparten, no multiplican" el presupuesto.
     */
    /**
     * F5/F6: abre el panel de resultados en vivo (`BubbleOverlay.showResults`) si hay alguna
     * herramienta activa, y va actualizandolo una herramienta a la vez segun se resuelven (en
     * serie: `selectionSpent`/`selectionPaidCalls` se acumulan de verdad entre ellas). Devuelve un
     * mensaje SOLO si ni siquiera pudo arrancar (sin modelo configurado) — el resto de resultados
     * (listo/fallido/denegado) van al panel, no a un toast.
     */
    private suspend fun runAnalyses(
        text: String,
        imageWebpBase64: String?,
        entities: ExtractedEntities,
        c2pa: C2paDetection?,
        bubbleSettings: BubbleSettings,
        modelId: String?,
        packageName: String,
        appLabel: String?,
    ): String? {
        val container = (application as LupitaApp).container
        val effectiveId = ModelCatalog.effectiveSelection(container.modelCatalog, modelId)
        val model = container.modelCatalog.firstOrNull { it.id == effectiveId }
            ?: return getString(R.string.analysis_no_model)

        val activeTools = ToolId.entries.filter { it in bubbleSettings.enabledTools }
        if (activeTools.isEmpty()) return null

        // Agrupa las herramientas de esta captura en el Historial (F5/F6: varias comparten una) y
        // deja el contexto guardado para que un "Reintentar" del panel sepa que volver a llamar.
        val sessionId = java.util.UUID.randomUUID().toString()
        resultsContext = ResultsContext(sessionId, text, bubbleSettings.depth, model, packageName, appLabel, imageWebpBase64, entities, c2pa)
        bubble?.showResults(activeTools)

        var selectionSpent = CostMicros.ZERO
        var selectionPaidCalls = 0
        for (tool in activeTools) {
            val outcome = runOneTool(sessionId, tool, text, bubbleSettings.depth, model, packageName, appLabel, selectionSpent, selectionPaidCalls, imageWebpBase64, entities, c2pa)
            if (outcome is AnalysisRunner.Outcome.Success) {
                selectionSpent += outcome.cost
                selectionPaidCalls++
            }
        }
        return null
    }

    /**
     * Ejecuta UNA herramienta: llama, guarda en Historial (con el texto real, F5/F6) y actualiza el
     * panel en vivo. Reutilizado por el bucle de [runAnalyses] y por [retryAnalysis].
     */
    private suspend fun runOneTool(
        sessionId: String,
        tool: ToolId,
        text: String,
        depth: Depth,
        model: ModelOption,
        packageName: String,
        appLabel: String?,
        selectionSpent: CostMicros,
        selectionPaidCalls: Int,
        imageWebpBase64: String? = null,
        entities: ExtractedEntities = ExtractedEntities(),
        c2pa: C2paDetection? = null,
    ): AnalysisRunner.Outcome {
        val container = (application as LupitaApp).container
        val (capabilityId, prompt) = promptFor(tool)
        val textForTool = contextFor(tool, entities, c2pa)?.let { "$it\n\n---\n\n$text" } ?: text
        val outcome = container.analysisRunner.run(tool, capabilityId, prompt, textForTool, depth, model, selectionSpent, selectionPaidCalls, imageWebpBase64)
        val success = outcome as? AnalysisRunner.Outcome.Success
        container.analysisHistory.record(
            AnalysisHistoryEntry(
                sessionId = sessionId,
                timestampMillis = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = appLabel,
                tool = tool,
                capability = capabilityId,
                model = model.id,
                succeeded = success != null,
                text = success?.text,
                reason = if (success == null) failureText(outcome) else null,
                cost = success?.cost ?: CostMicros.ZERO,
            ),
        )
        bubble?.updateResult(
            tool,
            if (success != null) ToolResultState.Ready(success.text) else ToolResultState.Failed(failureText(outcome)),
        )
        return outcome
    }

    /**
     * "Reintentar" desde el panel (F5/F6): una llamada suelta, sin acumular con lo ya gastado por
     * las demas herramientas de la seleccion original — limitacion conocida y documentada (un
     * reintento manual de una sola herramienta no vuelve a sumar `selectionSpent`/`selectionPaidCalls`
     * de las que ya se resolvieron), sigue verificando su propio tope de herramienta/global.
     */
    private fun retryAnalysis(tool: ToolId) {
        val ctx = resultsContext ?: return
        scope.launch {
            runOneTool(
                ctx.sessionId, tool, ctx.text, ctx.depth, ctx.model, ctx.packageName, ctx.appLabel,
                CostMicros.ZERO, 0, ctx.imageWebpBase64, ctx.entities, ctx.c2pa,
            )
        }
    }

    /** Contexto de la ultima captura con el panel de resultados abierto — lo necesita [retryAnalysis]. */
    private data class ResultsContext(
        val sessionId: String,
        val text: String,
        val depth: Depth,
        val model: ModelOption,
        val packageName: String,
        val appLabel: String?,
        val imageWebpBase64: String?,
        val entities: ExtractedEntities,
        val c2pa: C2paDetection?,
    )

    // GENERAL en V2 (negrita, no tiene contexto de F3 que cablear). VERIFY/AI_DETECT/ENTITY en V3
    // (2026-09-25): negrita + instrucciones para el contexto determinista que anade [contextFor].
    // Ninguna V1/V2 se edita in situ.
    private fun promptFor(tool: ToolId): Pair<String, String> = when (tool) {
        ToolId.GENERAL -> GeneralAnalysisPromptV2.CAPABILITY_ID to GeneralAnalysisPromptV2.system
        ToolId.VERIFY -> VerifyPromptV3.CAPABILITY_ID to VerifyPromptV3.system
        ToolId.AI_DETECT -> AiDetectPromptV3.CAPABILITY_ID to AiDetectPromptV3.system
        ToolId.ENTITY -> EntityPromptV3.CAPABILITY_ID to EntityPromptV3.system
    }

    /**
     * Contexto determinista de F3 anadido ANTES del texto normalizado, especifico de cada
     * herramienta (2026-09-25, cierra el hueco que cada V1 ya anunciaba en su doc comment): `null`
     * si no hay nada que anadir, el llamador no toca el texto en ese caso.
     */
    private suspend fun contextFor(tool: ToolId, entities: ExtractedEntities, c2pa: C2paDetection?): String? =
        when (tool) {
            ToolId.ENTITY -> entities.asPromptContext()
            ToolId.AI_DETECT -> c2pa?.asPromptContext()
            ToolId.VERIFY -> fetchedWebContext(entities.urls)
            ToolId.GENERAL -> null
        }

    /**
     * Solo para Verificacion de hechos: si el texto cita una URL, la descarga de verdad (F3,
     * `WebFetcher`) y saca su cuerpo legible (`Readability`) — primer paso real hacia F4.5. Prueba
     * solo la PRIMERA url (una llamada de red por captura, no una por cada enlace citado); `null`
     * ante cualquier fallo (sin red, pagina no HTML, cuerpo vacio) sin bloquear el analisis.
     */
    private suspend fun fetchedWebContext(urls: List<String>): String? {
        val url = urls.firstOrNull() ?: return null
        val container = (application as LupitaApp).container
        val success = container.webFetcher.fetch(url) as? FetchResult.Success ?: return null
        val contentType = success.contentType
        if (contentType != null && !contentType.contains("html", ignoreCase = true)) return null
        return Readability.extract(success.body.take(HTML_FETCH_CAP), url).asPromptContext(url)
    }

    /** Texto de un desenlace SIN exito — usado como `reason` en Historial y como cuerpo del estado
     * `Failed` del panel. Nunca se llama con un `Success` de verdad (ver [runOneTool]). */
    private fun failureText(outcome: AnalysisRunner.Outcome): String = when (outcome) {
        is AnalysisRunner.Outcome.Success -> outcome.text
        is AnalysisRunner.Outcome.Denied -> denyReasonText(outcome.reason)
        is AnalysisRunner.Outcome.Failed -> getString(R.string.analysis_failed, outcome.reason)
        AnalysisRunner.Outcome.NoApiKey -> getString(R.string.analysis_no_api_key)
        AnalysisRunner.Outcome.NoPricing -> getString(R.string.analysis_no_pricing)
    }

    private fun denyReasonText(reason: DenyReason): String = getString(
        when (reason) {
            DenyReason.DEPTH_COST -> R.string.analysis_denied_depth_cost
            DenyReason.DEPTH_CALLS -> R.string.analysis_denied_depth_calls
            DenyReason.TOOL_COST -> R.string.analysis_denied_tool_cost
            DenyReason.GLOBAL_COST -> R.string.analysis_denied_global_cost
        },
    )

    /** Quita la burbuja y cierra la app: la X de abajo y el boton del panel. */
    private fun quit() {
        ProjectionSession.stop()
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun summarize(
        outcome: CapturePipeline.Outcome,
        ms: Long,
        settleMs: Long,
        savePolicy: ImageSavePolicy,
        recorderEnabled: Boolean,
        auditEnabled: Boolean,
        bubbleSettings: BubbleSettings,
        modelId: String?,
    ): Summary = when (outcome) {
        is CapturePipeline.Outcome.Failed ->
            Summary(getString(R.string.capture_failed, outcome.reason.name, outcome.detail ?: "-"))

        is CapturePipeline.Outcome.Ready -> {
            val bundle = outcome.bundle
            val content = bundle.content
            if (content == null) {
                if (auditEnabled) appendAudit(bundle.header, kilobytes = 0)
                Summary(getString(R.string.capture_protected, bundle.header.decisionSource.name))
            } else {
                val image = content.pixels
                val encoded = image?.let {
                    withContext(Dispatchers.Default) { RegionImage.encode(it, content.provenance, settleMs) }
                }
                val kb = (encoded?.bytes?.size ?: 0) / 1024
                // Protegidas nunca llegan aqui (content == null arriba): la politica solo se aplica a lo leido.
                if (encoded != null && recorderEnabled) handleSave(bundle, encoded, savePolicy.effective(bundle.header.tier))
                if (auditEnabled) appendAudit(bundle.header, kb)
                // F2: normalizacion determinista (filtrado, orden de lectura, roles) + artefacto por hash del texto.
                val normalized = ContextNormalizer.normalize(content.nodes)
                val textArtifact = ArtifactHash.of(normalized.plainText)
                // Bug real corregido 2026-09-25: la imagen ya viaja codificada en WEBP (`encoded`, de
                // arriba, para la politica de guardado) — reutilizarla en base64 en vez de descartarla
                // era el hueco por el que la IA nunca veia el recorte, solo el texto.
                val imageWebpBase64 = encoded?.bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
                // F3 cableado al flujo real (2026-09-25): OCR SOLO si el arbol dio muy poco texto
                // (memes, capturas de imagen pura) — con texto ya sustancioso no aporta nada, solo
                // anadiria latencia. El texto que ven las 4 herramientas incluye el OCR cuando corrio;
                // `textArtifact`/`capture_normalized` de arriba se quedan atados al texto del arbol
                // solo, para que el hash siga significando "lo que el arbol dio" sin mezclarlo.
                val ocrText = if (image != null && OcrGate.shouldRun(normalized.plainText)) {
                    val bitmap = withContext(Dispatchers.Default) {
                        android.graphics.Bitmap.createBitmap(image.argb, image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
                    }
                    try {
                        TextOcr.recognize(bitmap).fullText.takeIf { it.isNotBlank() }
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    null
                }
                val analysisText = if (ocrText != null) "${normalized.plainText}\n\n$ocrText".trim() else normalized.plainText
                // Extraccion determinista (F3, `EntityExtractor`) sobre el texto YA enriquecido con el
                // OCR — un enlace o mencion visible solo en la imagen (meme, captura de pantalla) tambien
                // cuenta. Senal C2PA (F3, `C2paDetector`) sobre los bytes de la imagen, si la hay.
                val entities = EntityExtractor.extract(analysisText)
                val c2pa = encoded?.bytes?.let { C2paDetector.detect(it) }
                // F5/F6: abre el panel de resultados en vivo (BubbleOverlay.showResults/updateResult) si hay
                // alguna herramienta activa; `analysisError` solo se rellena si ni siquiera pudo arrancar.
                val analysisError = runAnalyses(
                    analysisText, imageWebpBase64, entities, c2pa, bubbleSettings, modelId,
                    bundle.header.packageName, bundle.header.appLabel,
                )
                Summary(
                    message = getString(
                        R.string.capture_read,
                        bundle.header.tier.name,
                        content.nodes.sumOf { n -> n.flatten().count() },
                        image?.width ?: 0,
                        image?.height ?: 0,
                        kb,
                        ms,
                    ),
                    normalized = getString(
                        R.string.capture_normalized,
                        normalized.nodes.size,
                        getString(
                            if (normalized.pattern == ContentPattern.SOCIAL_POST) R.string.pattern_social_post else R.string.pattern_unknown,
                        ),
                        textArtifact.hex.take(8),
                    ),
                    analysisError = analysisError,
                )
            }
        }
    }

    /** Decidido 2026-09-22: siempre / preguntar / nunca. En «preguntar», una ventana propia lo confirma. */
    private fun handleSave(bundle: ContextBundle, encoded: EncodedImage, effective: ImageSavePolicy) {
        when (effective) {
            ImageSavePolicy.NEVER -> Unit
            ImageSavePolicy.ALWAYS -> debugSave(bundle, encoded)
            ImageSavePolicy.ASK -> {
                val label = bundle.header.appLabel ?: bundle.header.packageName
                val summary = getString(R.string.ask_save_summary, label, encoded.bytes.size / 1024)
                AskSaveOverlay(this).show(summary) { save -> if (save) debugSave(bundle, encoded) }
            }
        }
    }

    /**
     * SOLO en builds de depuracion, con el grabador de fixtures activado (F1.5, desactivado por defecto) y
     * nunca con una app protegida (no habria contenido): deja la imagen y un volcado del arbol en el
     * almacenamiento privado. No se sube ni se versiona.
     */
    private fun debugSave(bundle: ContextBundle, encoded: EncodedImage) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        runCatching {
            val dir = File(filesDir, "debug-captures").apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            File(dir, "$stamp.webp").writeBytes(encoded.bytes)
            File(dir, "$stamp.txt").writeText(
                buildString {
                    appendLine("app=${bundle.header.packageName} tier=${bundle.header.tier}")
                    appendLine("record=${encoded.record.toJson()}")
                    appendLine("header=${bundle.header}")
                    bundle.content?.nodes?.forEach { root ->
                        root.flatten().forEach { n ->
                            appendLine("${n.className} ${n.bounds} pw=${n.isPassword} ed=${n.isEditable} text=${n.text} desc=${n.contentDescription}")
                        }
                    }
                },
            )
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /**
     * Abre la app en la seccion pedida desde el menu. Permitido aunque el servicio este en segundo
     * plano porque la app tiene una ventana de superposicion visible.
     */
    private fun openApp(section: AppSection) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SECTION, section.key)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        showBubbleIfNeeded()
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, getString(R.string.notification_action_stop), stop).build())
            .build()
    }

    companion object {
        /** Tiempo para que desaparezca la capa de captura antes de pedir la imagen (a medir en el movil). */
        private const val CAPTURE_SETTLE_MS = 150L
        private const val PROJECTION_SETTLE_MS = 600L

        /** Separacion entre los dos toasts del resumen: que no se pisen ni se lean como uno solo. */
        private const val TOAST_GAP_MS = 3500L

        /** Tope de HTML leido antes de pasarlo a Jsoup en [fetchedWebContext] — pagina real, no la
         * pantalla del usuario; una defensa barata contra una pagina desmesurada. */
        private const val HTML_FETCH_CAP = 500_000
        const val ACTION_STOP = "com.antoniopg.lupita.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1

        /** Solo debe llamarse con la app visible (ver la nota de arranque en segundo plano arriba). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
    }
}
