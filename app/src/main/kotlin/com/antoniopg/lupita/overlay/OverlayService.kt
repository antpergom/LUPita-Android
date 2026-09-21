package com.antoniopg.lupita.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.antoniopg.lupita.LupitaApp
import com.antoniopg.lupita.MainActivity
import com.antoniopg.lupita.R
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.ui.overlay.BubbleOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
        bubble = BubbleOverlay(this, settings, scope, onOpenApp = ::openApp).also { it.show() }
    }

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
