package com.antoniopg.lupita

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antoniopg.lupita.core.model.PermissionState
import com.antoniopg.lupita.core.model.RequiredPermission
import com.antoniopg.lupita.overlay.OverlayService
import com.antoniopg.lupita.ui.app.onboarding.OnboardingScreen
import com.antoniopg.lupita.ui.app.onboarding.ServiceActiveScreen

class MainActivity : ComponentActivity() {

    private var permissions by mutableStateOf(PermissionState(overlay = false, notifications = false))

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Obligatorio (es la unica via de apagar la burbuja). Si el usuario lo deniega, el sistema
            // deja de mostrar el dialogo tras un par de negativas: se le lleva a los ajustes.
            if (!granted) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Paleta segun el sistema. Sin esto, MaterialTheme por defecto es CLARO y con el movil en
            // modo oscuro el texto salia oscuro sobre fondo oscuro (visto en el dispositivo). La
            // Surface fija fondo y color de texto coherentes. Provisional: el tema real llega con el mock.
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val missing = permissions.missing()
                    if (missing.isEmpty()) {
                        ServiceActiveScreen()
                    } else {
                        OnboardingScreen(missing = missing, onRequest = ::request)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Tambien al volver de los ajustes del sistema tras conceder un permiso.
        refresh()
    }

    private fun refresh() {
        permissions = readPermissions()
        // Decidido: abrir la app SIEMPRE arranca la burbuja, esté apagada o no. Es la unica via de
        // volver a encenderla (solo se apaga desde la notificacion).
        if (permissions.allGranted) OverlayService.start(this)
    }

    private fun readPermissions() = PermissionState(
        overlay = Settings.canDrawOverlays(this),
        notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
    )

    private fun request(permission: RequiredPermission) {
        when (permission) {
            RequiredPermission.OVERLAY -> startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
            RequiredPermission.NOTIFICATIONS -> requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
