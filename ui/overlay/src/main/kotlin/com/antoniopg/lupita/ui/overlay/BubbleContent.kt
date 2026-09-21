package com.antoniopg.lupita.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaTheme

/** Tamano de la burbuja y margen alrededor (sombra, insignia y separacion del borde), en dp. */
internal const val BUBBLE_SIZE_DP = 58
internal const val BUBBLE_MARGIN_DP = 8

/**
 * La burbuja segun el mock: gris (sin herramientas) o de color con brillo (lista), con una insignia
 * con el numero de herramientas cuando hay mas de una. El margen exterior (8 dp) deja sitio a la
 * sombra y a la insignia, que se salen del circulo, y da la separacion del borde de pantalla.
 */
@Composable
internal fun BubbleContent(activeCount: Int) {
    LupitaTheme {
        val c = Lupita.colors
        val active = activeCount > 0
        val background by animateColorAsState(
            targetValue = if (active) c.accent else c.cardLight,
            animationSpec = tween(150),
            label = "bubbleBackground",
        )
        val description = stringResource(R.string.bubble_description)
        Box(modifier = Modifier.padding(BUBBLE_MARGIN_DP.dp)) {
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE_DP.dp)
                    .shadow(
                        elevation = if (active) 10.dp else 6.dp,
                        shape = CircleShape,
                        ambientColor = if (active) c.accent else Color.Black,
                        spotColor = if (active) c.accent else Color.Black,
                    )
                    .background(background, CircleShape)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (active) c.onAccent else c.ink,
                    modifier = Modifier.size(26.dp),
                )
                if (activeCount > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .background(c.ink, CircleShape)
                            .border(2.dp, c.background, CircleShape)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = activeCount.toString(),
                            color = c.background,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}
