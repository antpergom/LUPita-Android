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
import com.antoniopg.lupita.capability.privacy.CapturePipeline
import com.antoniopg.lupita.capability.screen.AccessibilityScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionScreenSource
import com.antoniopg.lupita.capability.screen.ProjectionSession
import com.antoniopg.lupita.capability.screen.RegionImage
import com.antoniopg.lupita.capture.ProjectionConsentActivity
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.ContextBundle
import com.antoniopg.lupita.core.model.EncodedImage
import com.antoniopg.lupita.core.model.ScreenSource
import com.antoniopg.lupita.core.model.SelectionRect
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
        val started = SystemClock.elapsedRealtime()
        val outcome = withContext(Dispatchers.Default) {
            CapturePipeline(source, container.privacyGate).run(rect, settings)
        }
        toast(summarize(outcome, SystemClock.elapsedRealtime() - started, settleMs))
    }

    /** Quita la burbuja y cierra la app: la X de abajo y el boton del panel. */
    private fun quit() {
        ProjectionSession.stop()
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun summarize(outcome: CapturePipeline.Outcome, ms: Long, settleMs: Long): String = when (outcome) {
        is CapturePipeline.Outcome.Failed ->
            getString(R.string.capture_failed, outcome.reason.name, outcome.detail ?: "-")

        is CapturePipeline.Outcome.Ready -> {
            val bundle = outcome.bundle
            val content = bundle.content
            if (content == null) {
                getString(R.string.capture_protected, bundle.header.decisionSource.name)
            } else {
                val image = content.pixels
                val encoded = image?.let {
                    withContext(Dispatchers.Default) { RegionImage.encode(it, content.provenance, settleMs) }
                }
                if (encoded != null) debugSave(bundle, encoded)
                getString(
                    R.string.capture_read,
                    bundle.header.tier.name,
                    content.nodes.sumOf { n -> n.flatten().count() },
                    image?.width ?: 0,
                    image?.height ?: 0,
                    (encoded?.bytes?.size ?: 0) / 1024,
                    ms,
                )
            }
        }
    }

    /**
     * SOLO en builds de depuracion (y nunca con una app protegida: no habria contenido): deja la imagen y un
     * volcado del arbol en el almacenamiento privado, para medir tamanos y comprobar el filtrado por `adb`.
     * Es el germen del grabador de fixtures (F1.5); no se sube ni se versiona.
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
