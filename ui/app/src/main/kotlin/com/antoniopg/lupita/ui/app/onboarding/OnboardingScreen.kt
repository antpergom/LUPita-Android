package com.antoniopg.lupita.ui.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.RequiredPermission
import com.antoniopg.lupita.ui.app.R

/** Pide, uno a uno, los permisos que faltan. Cuando no falta ninguno, la Activity ya no la muestra. */
@Composable
fun OnboardingScreen(
    missing: List<RequiredPermission>,
    onRequest: (RequiredPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)
        missing.forEach { permission ->
            PermissionCard(permission = permission, onRequest = { onRequest(permission) })
        }
    }
}

@Composable
private fun PermissionCard(permission: RequiredPermission, onRequest: () -> Unit) {
    val (title, description) = when (permission) {
        RequiredPermission.OVERLAY ->
            R.string.permission_overlay_title to R.string.permission_overlay_description
        RequiredPermission.NOTIFICATIONS ->
            R.string.permission_notifications_title to R.string.permission_notifications_description
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
        }
    }
}

/**
 * Provisional (F0): se sustituye por la pantalla de Configuracion e Historial en el paso 5. Por ahora
 * solo muestra a que seccion se ha pedido ir desde el menu de la burbuja.
 */
@Composable
fun ServiceActiveScreen(requestedSection: AppSection? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.service_active_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.service_active_body), style = MaterialTheme.typography.bodyLarge)
        if (requestedSection != null) {
            val name = stringResource(
                when (requestedSection) {
                    AppSection.SETTINGS -> R.string.section_settings
                    AppSection.HISTORY -> R.string.section_history
                },
            )
            Text(stringResource(R.string.section_requested, name), style = MaterialTheme.typography.titleMedium)
        }
    }
}
