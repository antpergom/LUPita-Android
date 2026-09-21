package com.antoniopg.lupita.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.ui.theme.Lupita

/** Piezas comunes de Ajustes y de Privacidad, con la estetica del mock. */

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Lupita.colors.subtle,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/** Fila elegible: la seleccionada va en el color de acento (mock). */
@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    val c = Lupita.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accent else c.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showCheck && selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                color = if (selected) c.onAccent else c.ink,
                fontSize = if (selected) 14.sp else 13.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
            if (description != null) {
                Text(description, color = if (selected) c.onAccent else c.subtle, fontSize = 11.sp)
            }
        }
    }
}

/** Interruptor con titulo y descripcion; toda la fila es pulsable. */
@Composable
internal fun SwitchRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Lupita.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) Text(description, color = c.subtle, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.subtle,
                uncheckedTrackColor = c.cardLight,
                uncheckedBorderColor = c.divider,
            ),
        )
    }
}

/** Fila que abre otra pantalla. */
@Composable
internal fun NavRow(title: String, description: String, onClick: () -> Unit) {
    val c = Lupita.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = c.subtle, fontSize = 11.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.subtle)
    }
}

@Composable
internal fun tierLabel(tier: PrivacyTier): String = stringResource(
    when (tier) {
        PrivacyTier.PROTECTED -> R.string.tier_protected
        PrivacyTier.SENSITIVE -> R.string.tier_sensitive
        PrivacyTier.NORMAL -> R.string.tier_normal
    },
)

@Composable
internal fun tierDescription(tier: PrivacyTier): String = stringResource(
    when (tier) {
        PrivacyTier.PROTECTED -> R.string.tier_protected_desc
        PrivacyTier.SENSITIVE -> R.string.tier_sensitive_desc
        PrivacyTier.NORMAL -> R.string.tier_normal_desc
    },
)

/** Los tres niveles como fichas, para grupos y excepciones. */
@Composable
internal fun TierSelector(selected: PrivacyTier, onSelect: (PrivacyTier) -> Unit, modifier: Modifier = Modifier) {
    val c = Lupita.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PrivacyTier.entries.forEach { tier ->
            val isSelected = tier == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) c.accent else c.cardLight)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(tier) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tierLabel(tier),
                    color = if (isSelected) c.onAccent else c.ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
