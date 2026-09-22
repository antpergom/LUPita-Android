package com.antoniopg.lupita.ui.app.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/** Lo que hace falta para mostrar y activar el servicio de accesibilidad. La comprobacion vive en `:app`. */
class AccessibilityUi(val isEnabled: Boolean, val onOpenSettings: () -> Unit)

/**
 * Divulgacion previa a la accesibilidad, exigida por Google Play para apps que no son herramientas de
 * accesibilidad (ver docs/decisions/2026-09-21-privacidad-de-lo-capturado.md, punto 9): que se lee, que
 * no, y que puede salir del dispositivo, ANTES de llevar al usuario a activarlo en Ajustes del sistema
 * (la app no puede activarlo por si misma).
 */
@Composable
fun AccessibilityDisclosureScreen(ui: AccessibilityUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lupita.colors
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.privacy_back), tint = c.ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.accessibility_title),
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            StatusRow(enabled = ui.isEnabled)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.accessibility_intro), color = c.ink, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            DisclosureItem(true, stringResource(R.string.accessibility_reads))
            DisclosureItem(true, stringResource(R.string.accessibility_reads_region))
            DisclosureItem(false, stringResource(R.string.accessibility_never_protected))
            DisclosureItem(false, stringResource(R.string.accessibility_never_passwords))
            DisclosureItem(false, stringResource(R.string.accessibility_never_raw))
            DisclosureItem(true, stringResource(R.string.accessibility_may_leave))

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.accessibility_more_detail), color = c.subtle, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = ui.onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (ui.isEnabled) R.string.accessibility_manage else R.string.accessibility_activate,
                ),
            )
        }
    }
}

@Composable
private fun StatusRow(enabled: Boolean) {
    val c = Lupita.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent.copy(alpha = 0.14f) else c.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (enabled) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (enabled) c.accent else c.subtle,
        )
        Text(
            stringResource(if (enabled) R.string.accessibility_status_on else R.string.accessibility_status_off),
            color = c.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DisclosureItem(allowed: Boolean, text: String) {
    val c = Lupita.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (allowed) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (allowed) c.accent else c.subtle,
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
