package com.antoniopg.lupita.ui.app.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/** Una tarjeta de Historial — una captura, no una herramienta suelta (varias comparten `sessionId`, F5/F6). */
data class HistoryCard(val sessionId: String, val dateLabel: String, val zoneLabel: String, val tags: List<String>)

class HistoryUi(val cards: List<HistoryCard>)

/**
 * Historial: una tarjeta por captura (fecha, app de origen, herramientas usadas) — diseño del mock
 * de Claude Design (`LUPita.dc.html`, importado 2026-09-25). Antes de esta sesion, la persistencia
 * del resultado no existia (solo el coste, F4); ahora `AnalysisHistoryRepository.observeRecent()`
 * alimenta esta pantalla de verdad.
 */
@Composable
fun HistoryScreen(ui: HistoryUi, modifier: Modifier = Modifier) {
    val c = Lupita.colors
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.history_title),
            color = c.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LupitaFonts.heading,
        )
        Spacer(Modifier.height(18.dp))
        if (ui.cards.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = c.iconMuted, modifier = Modifier.size(48.dp))
                Text(
                    stringResource(R.string.history_empty_title),
                    color = c.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.history_empty_body),
                    color = c.subtle,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ui.cards, key = { it.sessionId }) { card -> HistorySessionCard(card) }
            }
        }
    }
}

@Composable
private fun HistorySessionCard(card: HistoryCard) {
    val c = Lupita.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = card.dateLabel.uppercase(),
            color = c.subtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.em,
        )
        Text(
            text = card.zoneLabel,
            color = c.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LupitaFonts.heading,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            card.tags.forEach { tag -> HistoryTag(tag) }
        }
    }
}

@Composable
private fun HistoryTag(label: String) {
    val c = Lupita.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.cardLight)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(label, color = c.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
