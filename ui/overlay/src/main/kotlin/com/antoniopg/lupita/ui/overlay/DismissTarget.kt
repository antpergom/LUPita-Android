package com.antoniopg.lupita.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaTheme

/** Margen alrededor del circulo en su ventana (deja sitio a la sombra y al crecimiento al acertar). */
internal const val DISMISS_MARGIN_DP = 16

/**
 * El circulo con una X que aparece abajo mientras se arrastra la burbuja. Crece y se enciende cuando la
 * burbuja esta encima ([active]): soltarla ahi la quita.
 */
@Composable
internal fun DismissTargetContent(active: Boolean) {
    LupitaTheme {
        val c = Lupita.colors
        val scale by animateFloatAsState(if (active) 1.25f else 1f, tween(120), label = "dismissScale")
        val background by animateColorAsState(
            if (active) c.accent else Color.Black.copy(alpha = 0.55f),
            tween(120),
            label = "dismissBackground",
        )
        Box(
            modifier = Modifier.size((DismissZone.TARGET_SIZE_DP + 2 * DISMISS_MARGIN_DP).dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(DismissZone.TARGET_SIZE_DP.dp)
                    .scale(scale)
                    .shadow(8.dp, CircleShape)
                    .background(background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dismiss_target),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
