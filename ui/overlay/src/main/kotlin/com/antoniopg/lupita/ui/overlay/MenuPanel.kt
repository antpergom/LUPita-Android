package com.antoniopg.lupita.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.BubbleSettings
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts
import com.antoniopg.lupita.ui.theme.LupitaTheme

private val PANEL_WIDTH = 300.dp
private val EDGE_MARGIN = 8.dp
private val GAP_TO_BUBBLE = 12.dp

/**
 * El menu de herramientas (mock): un panel junto a la burbuja con las 4 herramientas como
 * interruptores, la profundidad y los botones de ajustes e historial. Ocupa toda la pantalla para que
 * tocar fuera lo cierre; el panel se coloca encima de la burbuja (o debajo, si arriba no cabe).
 */
@Composable
internal fun MenuOverlay(
    settings: BubbleSettings,
    anchor: IntRect,
    /** `true` si la burbuja esta en la mitad derecha: el panel crece desde su esquina derecha. */
    growFromRight: Boolean,
    onDismiss: () -> Unit,
    onToggleTool: (ToolId, Boolean) -> Unit,
    onSelectDepth: (Depth) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onQuit: () -> Unit,
) {
    LupitaTheme {
        // El panel crece desde la esquina que toca la burbuja.
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 220)) }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    MenuPanel(
                        settings = settings,
                        onToggleTool = onToggleTool,
                        onSelectDepth = onSelectDepth,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory,
                        onQuit = onQuit,
                        modifier = Modifier.graphicsLayer {
                            val p = progress.value
                            alpha = p
                            scaleX = 0.85f + 0.15f * p
                            scaleY = 0.85f + 0.15f * p
                            transformOrigin = TransformOrigin(if (growFromRight) 1f else 0f, 1f)
                        },
                    )
                },
            ) { measurables, constraints ->
                val panelWidth = PANEL_WIDTH.roundToPx()
                val margin = EDGE_MARGIN.roundToPx()
                val gap = GAP_TO_BUBBLE.roundToPx()
                val panel = measurables.first().measure(
                    Constraints(minWidth = panelWidth, maxWidth = panelWidth, minHeight = 0, maxHeight = constraints.maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (anchor.right - panelWidth).coerceIn(margin, (constraints.maxWidth - panelWidth - margin).coerceAtLeast(margin))
                    val above = anchor.top - gap - panel.height
                    val y = if (above >= margin) {
                        above
                    } else {
                        (anchor.bottom + gap).coerceAtMost((constraints.maxHeight - panel.height - margin).coerceAtLeast(margin))
                    }
                    panel.place(x, y)
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(
    settings: BubbleSettings,
    onToggleTool: (ToolId, Boolean) -> Unit,
    onSelectDepth: (Depth) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lupita.colors
    val shape = RoundedCornerShape(26.dp)
    val activeCount = settings.enabledTools.size
    Column(
        modifier = modifier
            .width(PANEL_WIDTH)
            .shadow(elevation = 24.dp, shape = shape)
            .background(c.background, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.menu_title),
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
            Text(
                pluralStringResource(R.plurals.menu_active_count, activeCount, activeCount),
                color = c.subtle,
                fontSize = 11.sp,
            )
        }
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolId.entries.forEach { tool ->
                ToolTile(tool = tool, active = tool in settings.enabledTools, onToggle = { onToggleTool(tool, it) })
            }
        }
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp)) {
            Text(
                stringResource(R.string.menu_depth_title),
                color = c.subtle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.em,
            )
            Spacer(Modifier.height(6.dp))
            DepthSelector(selected = settings.depth, onSelect = onSelectDepth)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            RoundIconButton(Icons.Rounded.Settings, stringResource(R.string.menu_settings), onOpenSettings)
            RoundIconButton(Icons.Rounded.History, stringResource(R.string.menu_history), onOpenHistory)
            // Esquina inferior derecha: cierra la burbuja y la app (pedido del usuario).
            RoundIconButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.menu_quit), onQuit, tint = c.accent)
        }
    }
}

private fun ToolId.icon(): ImageVector = when (this) {
    ToolId.GENERAL -> Icons.Rounded.GridView
    ToolId.VERIFY -> Icons.Rounded.Verified
    ToolId.AI_DETECT -> Icons.Rounded.AutoAwesome
    ToolId.ENTITY -> Icons.Rounded.PersonSearch
}

private fun ToolId.label(): Int = when (this) {
    ToolId.GENERAL -> R.string.tool_general
    ToolId.VERIFY -> R.string.tool_verify
    ToolId.AI_DETECT -> R.string.tool_ai_detect
    ToolId.ENTITY -> R.string.tool_entity
}

@Composable
private fun ToolTile(tool: ToolId, active: Boolean, onToggle: (Boolean) -> Unit) {
    val c = Lupita.colors
    val background by animateColorAsState(
        targetValue = if (active) c.accent else c.cardLight,
        animationSpec = tween(120),
        label = "tileBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .toggleable(value = active, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = tool.icon(),
            contentDescription = null,
            tint = if (active) c.iconOnAccent else c.iconMuted,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = stringResource(tool.label()),
            color = if (active) c.onAccent else c.ink,
            fontSize = if (active) 14.sp else 13.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun Depth.label(): Int = when (this) {
    Depth.LOW -> R.string.depth_low
    Depth.MEDIUM -> R.string.depth_medium
    Depth.HIGH -> R.string.depth_high
}

/** Control segmentado de 3 posiciones con un indicador que se desliza. */
@Composable
private fun DepthSelector(selected: Depth, onSelect: (Depth) -> Unit) {
    val c = Lupita.colors
    val depths = Depth.entries
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardLight)
            .padding(4.dp),
    ) {
        val segment = maxWidth / depths.size
        val thumbOffset by animateDpAsState(
            targetValue = segment * depths.indexOf(selected),
            animationSpec = tween(180),
            label = "depthThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segment)
                .height(SEGMENT_HEIGHT)
                .background(c.accent, RoundedCornerShape(10.dp)),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            depths.forEach { depth ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SEGMENT_HEIGHT)
                        .selectable(selected = depth == selected, role = Role.RadioButton, onClick = { onSelect(depth) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(depth.label()),
                        color = if (depth == selected) c.onAccent else c.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 32.dp

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Lupita.colors.ink,
) {
    val c = Lupita.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.card)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(17.dp))
    }
}
