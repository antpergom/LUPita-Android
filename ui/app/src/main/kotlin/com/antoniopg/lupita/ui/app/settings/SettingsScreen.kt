package com.antoniopg.lupita.ui.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.LanguageOption
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.theme.Lupita

/**
 * Ajustes (mock): informacion de la app, modelo de IA e idioma. El catalogo de modelos llega como
 * datos: con la lista vacia se muestra un aviso, sin tocar la pantalla para anadir el primero.
 */
@Composable
fun SettingsScreen(
    appName: String,
    appVersion: String,
    models: List<ModelOption>,
    selectedModelId: String?,
    onSelectModel: (String) -> Unit,
    languages: List<LanguageOption>,
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lupita.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.settings_title), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        SectionLabel(stringResource(R.string.settings_info))
        InfoRow(stringResource(R.string.settings_info_app), appName)
        InfoRow(stringResource(R.string.settings_info_version), appVersion)
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.settings_model))
        if (models.isEmpty()) {
            Text(stringResource(R.string.settings_model_empty), color = c.subtle, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model ->
                    ChoiceRow(
                        label = model.label,
                        selected = model.id == selectedModelId,
                        showCheck = false,
                        onClick = { onSelectModel(model.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.settings_language))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { language ->
                ChoiceRow(
                    label = language.displayName,
                    selected = language.tag == currentLanguage,
                    showCheck = true,
                    onClick = { onSelectLanguage(language.tag) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_language_more), color = c.subtle, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Lupita.colors.subtle,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = Lupita.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = c.ink, fontSize = 13.sp)
        Text(value, color = c.subtle, fontSize = 13.sp)
    }
    HorizontalDivider(color = c.divider)
}

/** Fila elegible: la seleccionada va en el color de acento (mock). */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, showCheck: Boolean, onClick: () -> Unit) {
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
        Text(
            text = label,
            color = if (selected) c.onAccent else c.ink,
            fontSize = if (selected) 14.sp else 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}
