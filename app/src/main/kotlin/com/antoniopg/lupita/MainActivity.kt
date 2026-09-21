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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.PermissionState
import com.antoniopg.lupita.core.model.RequiredPermission
import com.antoniopg.lupita.overlay.OverlayService
import com.antoniopg.lupita.ui.app.onboarding.OnboardingScreen
import com.antoniopg.lupita.ui.app.onboarding.ServiceActiveScreen
import com.antoniopg.lupita.ui.theme.LupitaTheme

class MainActivity : ComponentActivity() {

    private var permissions by mutableStateOf(PermissionState(overlay = false, notifications = false))

    /** Seccion pedida desde el menu de la burbuja (paso 5 la muestra de verdad). */
    private var section by mutableStateOf<AppSection?>(null)

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
        section = AppSection.fromKey(intent?.getStringExtra(EXTRA_SECTION))
        setContent {
            // Tema del mock (claro/oscuro segun el sistema). La Surface fija fondo y color de texto
            // coherentes: sin ella el texto salia oscuro sobre fondo oscuro (visto en el dispositivo).
            LupitaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val missing = permissions.missing()
                    if (missing.isEmpty()) {
                        ServiceActiveScreen(requestedSection = section)
                    } else {
                        OnboardingScreen(missing = missing, onRequest = ::request)
                    }
                }
            }
        }
    }

    // Con la app ya abierta, el menu de la burbuja reutiliza esta Activity (CLEAR_TOP + SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        section = AppSection.fromKey(intent.getStringExtra(EXTRA_SECTION))
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

    companion object {
        const val EXTRA_SECTION = "com.antoniopg.lupita.extra.SECTION"
    }
}
