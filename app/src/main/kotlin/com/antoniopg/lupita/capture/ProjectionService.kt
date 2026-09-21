package com.antoniopg.lupita.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.WindowManager
import com.antoniopg.lupita.R
import com.antoniopg.lupita.capability.screen.ProjectionSession

/**
 * Servicio en primer plano de tipo `mediaProjection` (obligatorio desde Android 14 ANTES de obtener la
 * proyeccion) que sostiene la sesion de captura del respaldo sin accesibilidad. Su notificacion tiene un
 * boton para pararla: el permiso es por sesion y la sesion no debe quedarse abierta sin que se vea.
 */
class ProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ProjectionSession.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        // Primero el servicio en primer plano y DESPUES la proyeccion: al reves, Android 14 lanza SecurityException.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        if (projection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ProjectionSession.onStopped = { stopSelf() }
        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        ProjectionSession.start(projection, bounds.width(), bounds.height(), resources.displayMetrics.densityDpi)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ProjectionSession.onStopped = null
        ProjectionSession.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.projection_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.projection_notification_title))
            .setContentText(getString(R.string.projection_notification_text))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.projection_stop),
                    stop,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.antoniopg.lupita.action.STOP_PROJECTION"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "projection_service"
        private const val NOTIFICATION_ID = 2

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ProjectionService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)

        private fun stopIntent(context: Context) =
            Intent(context, ProjectionService::class.java).setAction(ACTION_STOP)
    }
}
