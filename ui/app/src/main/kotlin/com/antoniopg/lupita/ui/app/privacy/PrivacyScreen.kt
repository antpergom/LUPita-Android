package com.antoniopg.lupita.ui.app.privacy

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.AuditEntry
import com.antoniopg.lupita.core.model.AuditOutcome
import com.antoniopg.lupita.core.model.ImageSavePolicy
import com.antoniopg.lupita.core.model.PendingSuggestion
import com.antoniopg.lupita.core.model.InstalledApp
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.UserRule
import com.antoniopg.lupita.core.model.localized
import com.antoniopg.lupita.ui.app.ChoiceRow
import com.antoniopg.lupita.ui.app.R
import com.antoniopg.lupita.ui.app.SectionLabel
import com.antoniopg.lupita.ui.app.SwitchRow
import com.antoniopg.lupita.ui.app.TierSelector
import com.antoniopg.lupita.ui.app.savePolicyDescription
import com.antoniopg.lupita.ui.app.savePolicyLabel
import com.antoniopg.lupita.ui.app.tierDescription
import com.antoniopg.lupita.ui.app.tierLabel
import com.antoniopg.lupita.ui.theme.Lupita
import com.antoniopg.lupita.ui.theme.LupitaFonts

/** Todo lo que la ventana puede cambiar. Cada accion persiste en el acto; se aplican en la siguiente captura. */
class PrivacyActions(
    val onMeasure: (SecurityMeasure, Boolean) -> Unit,
    val onUnknownTier: (PrivacyTier) -> Unit,
    /** `null` restablece el nivel del catalogo para ese grupo. */
    val onGroupTier: (String, PrivacyTier?) -> Unit,
    val onRegion: (String, Boolean) -> Unit,
    val onPutRule: (UserRule) -> Unit,
    val onRemoveRule: (AppMatch) -> Unit,
    val onImageSavePolicy: (ImageSavePolicy) -> Unit,
    val onAcceptSuggestion: (PendingSuggestion) -> Unit,
    val onDismissSuggestion: (String) -> Unit,
    val onClearAuditLog: () -> Unit,
    val loadInstalledApps: suspend () -> List<InstalledApp>,
)

class PrivacyUi(
    val settings: PrivacySettings,
    val catalog: PrivacyCatalog,
    val language: String,
    val actions: PrivacyActions,
)

/**
 * Ajustes -> Privacidad y seguridad: una sola ventana, sencilla. Que apps se leen y con que cuidado
 * (nivel para desconocidas, regiones, grupos y excepciones por app) y cada medida de seguridad como un
 * interruptor. Las medidas vienen activadas: desactivar una es una decision explicita.
 */
@Composable
fun PrivacyScreen(ui: PrivacyUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val c = Lupita.colors
    val s = ui.settings
    val actions = ui.actions
    var picking by remember { mutableStateOf(false) }
    val installed by produceState(emptyList<InstalledApp>()) { value = runCatching { actions.loadInstalledApps() }.getOrDefault(emptyList()) }
    val labels = remember(installed) { installed.associate { it.packageName to it.label } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val back = stringResource(R.string.privacy_back)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = back, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(R.string.privacy_title),
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LupitaFonts.heading,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.privacy_intro), color = c.subtle, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        // 1) Apps desconocidas
        SectionLabel(stringResource(R.string.privacy_unknown_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrivacyTier.entries.forEach { tier ->
                ChoiceRow(
                    label = tierLabel(tier),
                    description = tierDescription(tier),
                    selected = tier == s.unknownAppTier,
                    showCheck = false,
                    onClick = { actions.onUnknownTier(tier) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.privacy_unknown_hint), color = c.subtle, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))

        // 2) Medidas de seguridad
        SectionLabel(stringResource(R.string.privacy_measures_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityMeasure.entries.forEach { measure ->
                SwitchRow(
                    title = stringResource(measureTitle(measure)),
                    description = stringResource(measureDescription(measure)),
                    checked = s.isEnabled(measure),
                    onCheckedChange = { actions.onMeasure(measure, it) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 2b) Guardado de la imagen enviada
        SectionLabel(stringResource(R.string.save_policy_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageSavePolicy.entries.forEach { policy ->
                ChoiceRow(
                    label = savePolicyLabel(policy),
                    description = savePolicyDescription(policy),
                    selected = policy == s.imageSavePolicy,
                    showCheck = false,
                    onClick = { actions.onImageSavePolicy(policy) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.save_policy_hint), color = c.subtle, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))

        // 3) Regiones
        SectionLabel(stringResource(R.string.privacy_regions_title))
        if (ui.catalog.regions.isEmpty()) {
            Text(stringResource(R.string.privacy_catalog_empty), color = c.subtle, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.catalog.regions.forEach { region ->
                    SwitchRow(
                        title = region.labels.localized(ui.language, region.id),
                        description = null,
                        checked = region.id in s.enabledRegions,
                        onCheckedChange = { actions.onRegion(region.id, it) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.privacy_regions_hint), color = c.subtle, fontSize = 11.sp)
        }
        Spacer(Modifier.height(20.dp))

        // 4) Grupos de apps
        SectionLabel(stringResource(R.string.privacy_groups_title))
        if (ui.catalog.groups.isEmpty()) {
            Text(stringResource(R.string.privacy_catalog_empty), color = c.subtle, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.catalog.groups.forEach { group ->
                    val effective = s.groupTiers[group.id] ?: group.defaultTier
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.card)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(group.labels.localized(ui.language, group.id), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TierSelector(
                            selected = effective,
                            // Elegir el nivel de fabrica del grupo equivale a restablecerlo.
                            onSelect = { tier -> actions.onGroupTier(group.id, tier.takeIf { it != group.defaultTier }) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.privacy_groups_hint), color = c.subtle, fontSize = 11.sp)
        }
        Spacer(Modifier.height(20.dp))

        // 5) Excepciones del usuario
        SectionLabel(stringResource(R.string.privacy_rules_title))
        if (s.userRules.isEmpty()) {
            Text(stringResource(R.string.privacy_rules_empty), color = c.subtle, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.userRules.forEach { rule ->
                    RuleCard(
                        rule = rule,
                        title = ruleTitle(rule.match, labels),
                        onTier = { actions.onPutRule(rule.copy(tier = it)) },
                        onRemove = { actions.onRemoveRule(rule.match) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent)
                .clickable(role = Role.Button, onClick = { picking = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.privacy_rules_add), color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(20.dp))

        // 6) Propuestas pendientes
        if (s.pendingSuggestions.isNotEmpty()) {
            SectionLabel(stringResource(R.string.privacy_suggestions_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.pendingSuggestions.forEach { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        appLabel = labels[suggestion.packageName],
                        onAccept = { actions.onAcceptSuggestion(suggestion) },
                        onDismiss = { actions.onDismissSuggestion(suggestion.packageName) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 7) Registro de auditoria
        SectionLabel(stringResource(R.string.privacy_audit_title))
        if (s.auditLog.isEmpty()) {
            Text(stringResource(R.string.privacy_audit_empty), color = c.subtle, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.auditLog.forEach { entry -> AuditRow(entry, labels[entry.packageName]) }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .clickable(role = Role.Button, onClick = actions.onClearAuditLog)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.privacy_audit_clear), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.privacy_apply_note), color = c.subtle, fontSize = 11.sp)
    }

    if (picking) {
        val taken = s.userRules.map { it.match }.toSet()
        AppPickerDialog(
            apps = installed.filter { AppMatch.Exact(it.packageName) !in taken },
            onPick = {
                // Lo habitual al anadir una excepcion es querer PROTEGER esa app.
                actions.onPutRule(UserRule(AppMatch.Exact(it.packageName), PrivacyTier.PROTECTED))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun RuleCard(rule: UserRule, title: Pair<String, String?>, onTier: (PrivacyTier) -> Unit, onRemove: () -> Unit) {
    val c = Lupita.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title.first, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                title.second?.let { Text(it, color = c.subtle, fontSize = 11.sp) }
            }
            val remove = stringResource(R.string.privacy_rules_remove)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = remove, tint = c.subtle, modifier = Modifier.size(16.dp))
            }
        }
        TierSelector(selected = rule.tier, onSelect = onTier)
    }
}

/** Titulo y subtitulo de una regla: el nombre de la app si esta instalada, y el paquete debajo. */
private fun ruleTitle(match: AppMatch, labels: Map<String, String>): Pair<String, String?> = when (match) {
    is AppMatch.Exact -> (labels[match.packageName] ?: match.packageName) to match.packageName.takeIf { it in labels }
    is AppMatch.Prefix -> "${match.prefix}.*" to null
}

@Composable
private fun AppPickerDialog(apps: List<InstalledApp>, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    val c = Lupita.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.background,
        title = { Text(stringResource(R.string.privacy_pick_app), color = c.ink, fontFamily = LupitaFonts.heading, fontWeight = FontWeight.Bold) },
        text = {
            if (apps.isEmpty()) {
                Text(stringResource(R.string.privacy_pick_empty), color = c.subtle, fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onPick(app) })
                                .padding(vertical = 8.dp),
                        ) {
                            Text(app.label, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, color = c.subtle, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.privacy_cancel), color = c.accent) } },
    )
}

private fun measureTitle(measure: SecurityMeasure): Int = when (measure) {
    SecurityMeasure.APP_CONTROL -> R.string.measure_app_control
    SecurityMeasure.DROP_PASSWORD_FIELDS -> R.string.measure_drop_password
    SecurityMeasure.PREVIEW_BEFORE_SEND -> R.string.measure_preview
    SecurityMeasure.REDACT_PATTERNS -> R.string.measure_redact
    SecurityMeasure.AUDIT_LOG -> R.string.measure_audit
    SecurityMeasure.PROPOSE_BY_NAME -> R.string.measure_propose
}

private fun measureDescription(measure: SecurityMeasure): Int = when (measure) {
    SecurityMeasure.APP_CONTROL -> R.string.measure_app_control_desc
    SecurityMeasure.DROP_PASSWORD_FIELDS -> R.string.measure_drop_password_desc
    SecurityMeasure.PREVIEW_BEFORE_SEND -> R.string.measure_preview_desc
    SecurityMeasure.REDACT_PATTERNS -> R.string.measure_redact_desc
    SecurityMeasure.AUDIT_LOG -> R.string.measure_audit_desc
    SecurityMeasure.PROPOSE_BY_NAME -> R.string.measure_propose_desc
}

/** Una propuesta por nombre pendiente: el nombre instalado si se conoce, si no el paquete. */
@Composable
private fun SuggestionCard(suggestion: PendingSuggestion, appLabel: String?, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val c = Lupita.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(appLabel ?: suggestion.packageName, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.privacy_suggestions_as, tierLabel(suggestion.tier)),
            color = c.subtle,
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cardLight)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.privacy_suggestions_dismiss), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accent)
                    .clickable(role = Role.Button, onClick = onAccept)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.privacy_suggestions_accept), color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Una linea del registro: solo metadatos (hora, app, nivel, resultado, tamano) — nunca contenido. */
@Composable
private fun AuditRow(entry: AuditEntry, appLabel: String?) {
    val c = Lupita.colors
    val outcome = stringResource(
        if (entry.outcome == AuditOutcome.PROTECTED) R.string.privacy_audit_protected else R.string.privacy_audit_read,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${formatAuditTime(entry.timestampMillis)} · ${appLabel ?: entry.packageName}",
            color = c.ink,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (entry.kilobytes > 0) "$outcome · ${entry.kilobytes} KB" else outcome,
            color = c.subtle,
            fontSize = 11.sp,
        )
    }
}

private fun formatAuditTime(millis: Long): String =
    java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
