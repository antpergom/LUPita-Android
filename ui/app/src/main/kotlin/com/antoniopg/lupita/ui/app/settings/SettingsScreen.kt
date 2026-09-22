package com.antoniopg.lupita.ui.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.LanguageOption
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.ui.app.ChoiceRow
import com.antoniopg.lupita.ui.app.NavRow
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.app.SectionLabel
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/**
 * Ajustes (mock): informacion de la app, privacidad, modelo de IA e idioma. El catalogo de modelos llega
 * como datos: con la lista vacia se muestra un aviso, sin tocar la pantalla para anadir el primero.
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
    onOpenPrivacy: () -> Unit,
    onOpenAccessibility: () -> Unit,
    /** `null` si no procede mostrarla (solo builds `debuggable`; ver `MainActivity`). */
    onOpenDebug: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Lupita.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            color = c.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LupitaFonts.heading,
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel(stringResource(R.string.settings_info))
        InfoRow(stringResource(R.string.settings_info_app), appName)
        InfoRow(stringResource(R.string.settings_info_version), appVersion)
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.settings_privacy))
        NavRow(
            title = stringResource(R.string.privacy_title),
            description = stringResource(R.string.privacy_row_desc),
            onClick = onOpenPrivacy,
        )
        Spacer(Modifier.height(8.dp))
        NavRow(
            title = stringResource(R.string.accessibility_row_title),
            description = stringResource(R.string.accessibility_row_desc),
            onClick = onOpenAccessibility,
        )
        if (onOpenDebug != null) {
            Spacer(Modifier.height(8.dp))
            NavRow(
                title = stringResource(R.string.debug_row_title),
                description = stringResource(R.string.debug_row_desc),
                onClick = onOpenDebug,
            )
        }
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
