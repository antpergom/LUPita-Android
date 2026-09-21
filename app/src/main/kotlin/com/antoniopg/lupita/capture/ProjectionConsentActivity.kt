package com.antoniopg.lupita.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.antoniopg.lupita.R

/**
 * Transparente: solo existe para lanzar el dialogo de permiso de proyeccion del sistema y pasarle el
 * resultado al [ProjectionService]. El permiso vale para UNA sesion. Desde Android 14 se pide la pantalla
 * completa (el usuario no puede elegir compartir una sola app, que dejaria la captura a medias).
 */
class ProjectionConsentActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(ProjectionService.startIntent(this, result.resultCode, data))
        } else {
            Toast.makeText(this, R.string.projection_denied, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // el dialogo ya esta en marcha
        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        launcher.launch(intent)
    }
}
