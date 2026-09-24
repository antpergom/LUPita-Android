package com.antoniopg.lupita.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/**
 * Estado de una herramienta dentro del panel (F5/F6 en vivo, sustituye a la notificacion anterior
 * — diseño del mock de Claude Design, `LUPita.dc.html`, importado 2026-09-25). [Failed] cubre tanto
 * un fallo real del proveedor como una denegacion de presupuesto o falta de clave/precio: el mock
 * solo distingue "listo" de "no se pudo completar", el motivo exacto va en el texto.
 */
sealed interface ToolResultState {
    data object Working : ToolResultState
    data class Ready(val text: String) : ToolResultState
    data class Failed(val reason: String) : ToolResultState
}

private val COLLAPSED_HEIGHT = 420.dp

/**
 * Panel de resultados: ventana a pantalla completa (bloquea los toques de detras, igual que
 * [CaptureOverlay]) con una hoja anclada abajo. Recibe todo por parametro — sin estado propio — para
 * que `BubbleOverlay` pueda actualizar una herramienta a la vez segun van terminando sus llamadas
 * reales (F6: varias herramientas comparten una captura, se resuelven en serie).
 */
@Composable
internal fun ResultsOverlay(
    tools: List<ToolId>,
    states: Map<ToolId, ToolResultState>,
    selected: ToolId?,
    expanded: Boolean,
    onSelectTab: (ToolId) -> Unit,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onRetry: (ToolId) -> Unit,
) {
    val c = Lupita.colors
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight() else Modifier.height(COLLAPSED_HEIGHT))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.background),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.results_title),
                    color = c.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LupitaFonts.heading,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultsIconButton(
                        icon = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        description = stringResource(if (expanded) R.string.results_collapse else R.string.results_expand),
                        onClick = onToggleExpand,
                    )
                    ResultsIconButton(Icons.Rounded.Close, stringResource(R.string.results_close), onClose)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tools.forEach { tool -> ResultTab(tool, states[tool], selected == tool) { onSelectTab(tool) } }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                val state = selected?.let { states[it] }
                when (state) {
                    is ToolResultState.Working -> selected?.let { ResultWorking(it) }
                    is ToolResultState.Failed -> selected?.let { ResultFailed(it, state.reason) { onRetry(it) } }
                    is ToolResultState.Ready -> Text(state.text, color = c.ink, fontSize = 13.sp, lineHeight = 19.sp)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ResultTab(tool: ToolId, state: ToolResultState?, selected: Boolean, onClick: () -> Unit) {
    val c = Lupita.colors
    val dotColor = when (state) {
        is ToolResultState.Ready -> c.accent
        is ToolResultState.Failed -> c.danger
        else -> c.subtle
    }
    val statusText = stringResource(
        when (state) {
            is ToolResultState.Ready -> R.string.results_status_ready
            is ToolResultState.Failed -> R.string.results_status_failed
            else -> R.string.results_status_working
        },
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.card else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(stringResource(tool.label()), color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            statusText,
            color = if (state is ToolResultState.Failed) c.danger else c.subtle,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ResultWorking(tool: ToolId) {
    val c = Lupita.colors
    Text(
        stringResource(R.string.results_working_body, stringResource(tool.label())),
        color = c.ink,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = c.accent,
        trackColor = c.track,
    )
}

@Composable
private fun ResultFailed(tool: ToolId, reason: String, onRetry: () -> Unit) {
    val c = Lupita.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.danger)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(stringResource(R.string.results_failed_pill), color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Text(reason, color = c.ink, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 12.dp))
    Button(onClick = onRetry) {
        Text(stringResource(R.string.results_retry))
    }
}

@Composable
private fun ResultsIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val c = Lupita.colors
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.card)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.ink, modifier = Modifier.size(17.dp))
    }
}
