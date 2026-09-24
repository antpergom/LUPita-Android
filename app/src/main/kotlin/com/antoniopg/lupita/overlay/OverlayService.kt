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
import com.antoniopg.lupita.capability.privacy.CapturePipeline
import com.antoniopg.lupita.capability.screen.AccessibilityScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionSession
import com.antoniopg.lupita.capability.screen.RegionImage
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
import com.antoniopg.lupita.core.model.ModelCatalog
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
import com.antoniopg.lupita.source.openai.AiDetectPromptV1
import com.antoniopg.lupita.source.openai.EntityPromptV1
import com.antoniopg.lupita.source.openai.GeneralAnalysisPromptV1
import com.antoniopg.lupita.source.openai.VerifyPromptV1
import com.antoniopg.lupita.ui.overlay.AskSaveOverlay
import com.antoniopg.lupita.ui.overlay.BubbleOverlay
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
        bubble = BubbleOverlay(this, settings, scope, onOpenApp = ::openApp, onCapture = ::onCapture, onQuit = ::quit)
            .also { it.show() }
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
        notifyAnalyses(summary.analyses)
    }

    private class Summary(val message: String, val normalized: String? = null, val analyses: List<String> = emptyList())

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
    private suspend fun runAnalyses(
        text: String,
        bubbleSettings: BubbleSettings,
        modelId: String?,
        packageName: String,
        appLabel: String?,
    ): List<String> {
        val container = (application as LupitaApp).container
        val effectiveId = ModelCatalog.effectiveSelection(container.modelCatalog, modelId)
        val model = container.modelCatalog.firstOrNull { it.id == effectiveId }
            ?: return listOf(getString(R.string.analysis_no_model))

        // Agrupa las herramientas de esta captura en el Historial (F5/F6: varias comparten una).
        val sessionId = java.util.UUID.randomUUID().toString()
        var selectionSpent = CostMicros.ZERO
        var selectionPaidCalls = 0
        val messages = mutableListOf<String>()
        for (tool in ToolId.entries) {
            if (tool !in bubbleSettings.enabledTools) continue
            val (capabilityId, prompt) = promptFor(tool)
            val outcome = container.analysisRunner.run(
                tool, capabilityId, prompt, text, bubbleSettings.depth, model, selectionSpent, selectionPaidCalls,
            )
            val succeeded = outcome is AnalysisRunner.Outcome.Success
            if (outcome is AnalysisRunner.Outcome.Success) {
                selectionSpent += outcome.cost
                selectionPaidCalls++
            }
            val outcomeText = analysisOutcomeText(outcome)
            container.analysisHistory.record(
                AnalysisHistoryEntry(
                    sessionId = sessionId,
                    timestampMillis = System.currentTimeMillis(),
                    packageName = packageName,
                    appLabel = appLabel,
                    tool = tool,
                    capability = capabilityId,
                    model = model.id,
                    succeeded = succeeded,
                    text = (outcome as? AnalysisRunner.Outcome.Success)?.text,
                    reason = if (succeeded) null else outcomeText,
                    cost = (outcome as? AnalysisRunner.Outcome.Success)?.cost ?: CostMicros.ZERO,
                ),
            )
            messages += getString(R.string.analysis_toast, toolLabel(tool), outcomeText)
        }
        return messages
    }

    private fun promptFor(tool: ToolId): Pair<String, String> = when (tool) {
        ToolId.GENERAL -> GeneralAnalysisPromptV1.CAPABILITY_ID to GeneralAnalysisPromptV1.system
        ToolId.VERIFY -> VerifyPromptV1.CAPABILITY_ID to VerifyPromptV1.system
        ToolId.AI_DETECT -> AiDetectPromptV1.CAPABILITY_ID to AiDetectPromptV1.system
        ToolId.ENTITY -> EntityPromptV1.CAPABILITY_ID to EntityPromptV1.system
    }

    private fun toolLabel(tool: ToolId): String = getString(
        when (tool) {
            ToolId.GENERAL -> R.string.analysis_tool_general
            ToolId.VERIFY -> R.string.analysis_tool_verify
            ToolId.AI_DETECT -> R.string.analysis_tool_ai_detect
            ToolId.ENTITY -> R.string.analysis_tool_entity
        },
    )

    private fun analysisOutcomeText(outcome: AnalysisRunner.Outcome): String = when (outcome) {
        is AnalysisRunner.Outcome.Success ->
            getString(R.string.analysis_result, outcome.text, "%.4f".format(java.util.Locale.US, outcome.cost.toUsd()))
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
                // F5/F6: una entrada por cada herramienta activa en la burbuja (vacio si ninguna lo esta).
                val analyses = runAnalyses(normalized.plainText, bubbleSettings, modelId, bundle.header.packageName, bundle.header.appLabel)
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
                    analyses = analyses,
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

    /**
     * Una notificacion por captura con el resultado de cada herramienta activa (una linea cada
     * una) — sustituye al toast por herramienta, que se topaba con la cuota de toasts de Android
     * en cuanto habia varias activas a la vez (bug real hallado verificando F5/F6, 2026-09-25).
     */
    private fun notifyAnalyses(messages: List<String>) {
        if (messages.isEmpty()) return
        val channel = NotificationChannel(
            ANALYSIS_CHANNEL_ID,
            getString(R.string.analysis_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        val style = Notification.InboxStyle()
        messages.forEach { style.addLine(it) }
        val notification = Notification.Builder(this, ANALYSIS_CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_notification))
            .setContentTitle(getString(R.string.analysis_notification_title))
            .setStyle(style)
            .setAutoCancel(true)
            .build()
        manager.notify(ANALYSIS_NOTIFICATION_ID, notification)
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
        const val ACTION_STOP = "com.antoniopg.lupita.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1

        // F5/F6, bug real (2026-09-25): con varias herramientas activas, Toast.makeText() se topa con
        // la cuota de toasts de Android y descarta los ultimos en silencio. Los resultados de analisis
        // van en una notificacion aparte (sin ese limite), una linea por herramienta.
        private const val ANALYSIS_CHANNEL_ID = "analysis_results"
        private const val ANALYSIS_NOTIFICATION_ID = 2

        /** Solo debe llamarse con la app visible (ver la nota de arranque en segundo plano arriba). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
    }
}
