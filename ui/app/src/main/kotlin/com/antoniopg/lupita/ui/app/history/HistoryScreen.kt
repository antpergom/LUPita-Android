package com.antoniopg.lupita.ui.app.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.theme.Lupita

/**
 * Historial. En F0 no hay analisis que listar (la persistencia llega con F4): solo el estado vacio. Las
 * tarjetas del mock (fecha, zona capturada y herramientas usadas) se anaden cuando haya datos reales.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val c = Lupita.colors
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.history_title), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
    }
}
