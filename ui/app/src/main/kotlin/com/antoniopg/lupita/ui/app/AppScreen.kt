package com.antoniopg.lupita.ui.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.LanguageOption
import com.antoniopg.lupita.core.model.ModelOption
import com.antoniopg.lupita.ui.app.history.HistoryScreen
import com.antoniopg.lupita.ui.app.accessibility.AccessibilityDisclosureScreen
import com.antoniopg.lupita.ui.app.accessibility.AccessibilityUi
import com.antoniopg.lupita.ui.app.aiprovider.AiProviderScreen
import com.antoniopg.lupita.ui.app.aiprovider.AiProviderUi
import com.antoniopg.lupita.ui.app.budget.BudgetScreen
import com.antoniopg.lupita.ui.app.budget.BudgetUi
import com.antoniopg.lupita.ui.app.debug.DebugScreen
import com.antoniopg.lupita.ui.app.debug.DebugUi
import com.antoniopg.lupita.ui.app.privacy.PrivacyScreen
import com.antoniopg.lupita.ui.app.privacy.PrivacyUi
import com.antoniopg.lupita.ui.app.settings.SettingsScreen
import com.antoniopg.lupita.ui.theme.Lupita

/**
 * La app: Ajustes (principal) e Historial en la misma ventana, con barra de navegacion inferior. Desde
 * Ajustes se abre la ventana de Privacidad, que se cierra con «atras» o al cambiar de seccion.
 *
 * [requestedSection] es una PETICION (p. ej. desde el menu de la burbuja), no un estado: se aplica y se
 * avisa con [onRequestConsumed] para que quien la guarda la olvide. Asi pedir «Ajustes» estando ya en
 * «Historial» cambia de pestana aunque el valor pedido sea igual que la vez anterior.
 */
@Composable
fun AppScreen(
    requestedSection: AppSection?,
    onRequestConsumed: () -> Unit,
    appName: String,
    appVersion: String,
    models: List<ModelOption>,
    selectedModelId: String?,
    onSelectModel: (String) -> Unit,
    languages: List<LanguageOption>,
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    privacy: PrivacyUi,
    budget: BudgetUi,
    aiProvider: AiProviderUi,
    accessibility: AccessibilityUi,
    /** `null` si no procede mostrarla (solo builds `debuggable`). */
    debug: DebugUi?,
    modifier: Modifier = Modifier,
) {
    val c = Lupita.colors
    var sectionKey by rememberSaveable { mutableStateOf((requestedSection ?: AppSection.SETTINGS).key) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var showAiProvider by rememberSaveable { mutableStateOf(false) }
    var showAccessibility by rememberSaveable { mutableStateOf(false) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedSection) {
        if (requestedSection != null) {
            sectionKey = requestedSection.key
            showPrivacy = false
            showBudget = false
            showAiProvider = false
            showAccessibility = false
            showDebug = false
            onRequestConsumed()
        }
    }
    val section = AppSection.fromKey(sectionKey) ?: AppSection.SETTINGS

    Scaffold(
        modifier = modifier,
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.background) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onAccent,
                    selectedTextColor = c.ink,
                    indicatorColor = c.accent,
                    unselectedIconColor = c.subtle,
                    unselectedTextColor = c.subtle,
                )
                NavigationBarItem(
                    selected = section == AppSection.SETTINGS,
                    onClick = {
                        sectionKey = AppSection.SETTINGS.key
                        showPrivacy = false
                        showBudget = false
                        showAiProvider = false
                        showAccessibility = false
                        showDebug = false
                    },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.section_settings)) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = section == AppSection.HISTORY,
                    onClick = {
                        sectionKey = AppSection.HISTORY.key
                        showPrivacy = false
                        showBudget = false
                        showAiProvider = false
                        showAccessibility = false
                        showDebug = false
                    },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.section_history)) },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        when {
            section == AppSection.HISTORY -> HistoryScreen(modifier = Modifier.padding(padding))
            showAccessibility -> AccessibilityDisclosureScreen(
                ui = accessibility,
                onBack = { showAccessibility = false },
                modifier = Modifier.padding(padding),
            )
            showDebug && debug != null -> DebugScreen(
                ui = debug,
                onBack = { showDebug = false },
                modifier = Modifier.padding(padding),
            )
            showBudget -> BudgetScreen(ui = budget, onBack = { showBudget = false }, modifier = Modifier.padding(padding))
            showAiProvider -> AiProviderScreen(ui = aiProvider, onBack = { showAiProvider = false }, modifier = Modifier.padding(padding))
            showPrivacy -> PrivacyScreen(ui = privacy, onBack = { showPrivacy = false }, modifier = Modifier.padding(padding))
            else -> SettingsScreen(
                appName = appName,
                appVersion = appVersion,
                models = models,
                selectedModelId = selectedModelId,
                onSelectModel = onSelectModel,
                languages = languages,
                currentLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage,
                onOpenPrivacy = { showPrivacy = true },
                onOpenBudget = { showBudget = true },
                onOpenAiProvider = { showAiProvider = true },
                onOpenAccessibility = { showAccessibility = true },
                onOpenDebug = debug?.let { { showDebug = true } },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
