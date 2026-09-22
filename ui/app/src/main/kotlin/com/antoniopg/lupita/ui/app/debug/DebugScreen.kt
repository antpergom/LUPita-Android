package com.antoniopg.lupita.ui.app.debug

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import com.antoniopg.lupita.ui.app.SectionLabel
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/** Un fichero del volcado de depuracion (`files/debug-captures/`): un `.txt` con su `.webp` a juego. */
data class DebugCaptureEntry(val id: String, val summary: String, val totalBytes: Long)

class DebugUi(
    val recorderEnabled: Boolean,
    val onRecorderEnabled: (Boolean) -> Unit,
    val captures: List<DebugCaptureEntry>,
    val onClearAll: () -> Unit,
)

/**
 * Pantalla de depuracion (solo builds `debuggable`; la fila que lleva aqui ya no aparece en release). El
 * grabador esta desactivado por defecto (decision 2026-09-22): sin el la app no escribe nada en
 * `files/debug-captures/`, ni siquiera con la politica de guardado en «siempre». Nunca hay entradas de una
 * app protegida (`content == null` antes de llegar al volcado, ver `OverlayService.summarize`).
 */
@Composable
fun DebugScreen(ui: DebugUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lupita.colors
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.privacy_back), tint = c.ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.debug_title),
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.card)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.debug_recorder_title), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.debug_recorder_desc), color = c.subtle, fontSize = 11.sp)
            }
            Switch(checked = ui.recorderEnabled, onCheckedChange = ui.onRecorderEnabled)
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.debug_captures_title))
        if (ui.captures.isEmpty()) {
            Text(stringResource(R.string.debug_captures_empty), color = c.subtle, fontSize = 12.sp)
        } else {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.captures.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.card)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(entry.summary, color = c.ink, fontSize = 12.sp)
                        Text("${entry.totalBytes / 1024} KB", color = c.subtle, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = ui.onClearAll)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.debug_clear_all), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
