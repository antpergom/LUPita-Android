package com.antoniopg.lupita

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antoniopg.lupita.core.model.AiCredentials
import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.AppSection
import com.antoniopg.lupita.core.model.BudgetSettings
import com.antoniopg.lupita.core.model.CostMicros
import com.antoniopg.lupita.core.model.Depth
import com.antoniopg.lupita.core.model.ModelCatalog
import com.antoniopg.lupita.core.model.PermissionState
import com.antoniopg.lupita.core.model.PrivacyRegions
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.capability.screen.LupitaAccessibilityService
import com.antoniopg.lupita.core.model.RequiredPermission
import com.antoniopg.lupita.core.model.ToolId
import com.antoniopg.lupita.core.model.UserRule
import com.antoniopg.lupita.overlay.OverlayService
import com.antoniopg.lupita.ui.app.AppScreen
import com.antoniopg.lupita.ui.app.accessibility.AccessibilityUi
import com.antoniopg.lupita.ui.app.aiprovider.AiProviderUi
import com.antoniopg.lupita.ui.app.budget.BudgetDepthRow
import com.antoniopg.lupita.ui.app.budget.BudgetToolRow
import com.antoniopg.lupita.ui.app.budget.BudgetUi
import com.antoniopg.lupita.ui.app.debug.DebugUi
import com.antoniopg.lupita.ui.app.privacy.PrivacyActions
import com.antoniopg.lupita.ui.app.privacy.PrivacyUi
import com.antoniopg.lupita.ui.app.onboarding.OnboardingScreen
import com.antoniopg.lupita.ui.theme.LupitaTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Envoltorio para distinguir «aun no leido» (`null`) de «leido y sin modelo elegido» (`StoredModel(null)`). */
private data class StoredModel(val id: String?)

class MainActivity : ComponentActivity() {

    private var permissions by mutableStateOf(PermissionState(overlay = false, notifications = false))
    private var accessibilityEnabled by mutableStateOf(false)

    /** Solo builds de desarrollo (`debuggable`): la fila de Depuracion no existe en release. */
    private val isDebugBuild by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private var debugRefresh by mutableStateOf(0)

    /** Sube al restablecer los topes de fabrica: fuerza a los campos de texto de Presupuesto a releer el valor. */
    private var budgetResetGeneration by mutableStateOf(0)

    /** Peticion de seccion desde el menu de la burbuja; la pantalla la aplica y la olvida. */
    private var section by mutableStateOf<AppSection?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Obligatorio (es la unica via de apagar la burbuja). Si el usuario lo deniega, el sistema
            // deja de mostrar el dialogo tras un par de negativas: se le lleva a los ajustes.
            if (!granted) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la PRIMERA vez: al cambiar de idioma Android recrea la Activity con el mismo Intent, y
        // releerlo volveria a aplicar la seccion pedida por la burbuja (te sacaria de Ajustes).
        if (savedInstanceState == null) {
            section = AppSection.fromKey(intent?.getStringExtra(EXTRA_SECTION))
        }
        val container = (application as LupitaApp).container
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            // Tema del mock (claro/oscuro segun el sistema). La Surface fija fondo y color de texto
            // coherentes: sin ella el texto salia oscuro sobre fondo oscuro (visto en el dispositivo).
            LupitaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val missing = permissions.missing()
                    if (missing.isEmpty()) {
                        val scope = rememberCoroutineScope()
                        val catalog = container.modelCatalog
                        // `null` = todavia no se ha leido lo guardado: no se resalta ninguno (en vez de
                        // mostrar un instante el primero y saltar despues al elegido).
                        val stored by remember { container.settings.modelId.map(::StoredModel) }
                            .collectAsState(initial = null)
                        val language = container.language.current()
                        val privacySettings by container.privacySettings.settings.collectAsState(
                            initial = PrivacySettings(enabledRegions = PrivacyRegions.defaultFor(container.language.systemCountry())),
                        )
                        val privacy = remember(scope, container, language, privacySettings) {
                            val repo = container.privacySettings
                            PrivacyUi(
                                settings = privacySettings,
                                catalog = container.privacyCatalog,
                                language = language,
                                actions = PrivacyActions(
                                    onMeasure = { m, on -> scope.launch { repo.setMeasure(m, on) } },
                                    onUnknownTier = { scope.launch { repo.setUnknownAppTier(it) } },
                                    onGroupTier = { g, t -> scope.launch { repo.setGroupTier(g, t) } },
                                    onRegion = { r, on -> scope.launch { repo.setRegionEnabled(r, on) } },
                                    onPutRule = { scope.launch { repo.putUserRule(it) } },
                                    onRemoveRule = { scope.launch { repo.removeUserRule(it) } },
                                    onImageSavePolicy = { scope.launch { repo.setImageSavePolicy(it) } },
                                    onAcceptSuggestion = {
                                        scope.launch {
                                            repo.putUserRule(UserRule(AppMatch.Exact(it.packageName), it.tier))
                                            repo.removeSuggestion(it.packageName)
                                        }
                                    },
                                    onDismissSuggestion = { scope.launch { repo.dismissSuggestion(it) } },
                                    onClearAuditLog = { scope.launch { repo.clearAuditLog() } },
                                    loadInstalledApps = { loadInstalledApps(applicationContext) },
                                ),
                            )
                        }
                        val budgetSettings by container.budgetSettings.settings.collectAsState(initial = BudgetSettings())
                        val budgetDefaults = container.budgetDefaults
                        val budget = remember(scope, budgetSettings, budgetDefaults, budgetResetGeneration) {
                            val repo = container.budgetSettings
                            BudgetUi(
                                depths = Depth.entries.map { depth ->
                                    val depthBudget = budgetSettings.depthBudget(depth, budgetDefaults)
                                    BudgetDepthRow(depth, depthBudget.costLimit.toUsd(), depthBudget.maxPaidCalls)
                                },
                                tools = ToolId.entries.map { tool ->
                                    BudgetToolRow(tool, budgetSettings.toolLimit(tool, budgetDefaults).toUsd())
                                },
                                globalLimitUsd = budgetSettings.effectiveGlobalLimit(budgetDefaults).toUsd(),
                                globalPeriod = budgetSettings.globalPeriod,
                                resetGeneration = budgetResetGeneration,
                                onDepthCostChange = { depth, usd ->
                                    scope.launch {
                                        val current = budgetSettings.depthBudget(depth, budgetDefaults)
                                        repo.setDepthBudget(depth, current.copy(costLimit = CostMicros.ofUsd(usd)))
                                    }
                                },
                                onDepthCallsChange = { depth, calls ->
                                    scope.launch {
                                        val current = budgetSettings.depthBudget(depth, budgetDefaults)
                                        repo.setDepthBudget(depth, current.copy(maxPaidCalls = calls))
                                    }
                                },
                                onToolLimitChange = { tool, usd -> scope.launch { repo.setToolLimit(tool, CostMicros.ofUsd(usd)) } },
                                onGlobalLimitChange = { usd -> scope.launch { repo.setGlobalLimit(CostMicros.ofUsd(usd)) } },
                                onGlobalPeriodChange = { period -> scope.launch { repo.setGlobalPeriod(period) } },
                                onReset = {
                                    scope.launch {
                                        repo.resetToDefaults()
                                        budgetResetGeneration++
                                    }
                                },
                            )
                        }
                        val aiCredentialsConfigured by remember { container.aiCredentials.credentials.map { it != null } }
                            .collectAsState(initial = false)
                        val aiProvider = remember(scope, aiCredentialsConfigured) {
                            val repo = container.aiCredentials
                            AiProviderUi(
                                keyConfigured = aiCredentialsConfigured,
                                onSave = { key -> scope.launch { repo.save(AiCredentials(key)) } },
                                onClear = { scope.launch { repo.clear() } },
                            )
                        }
                        AppScreen(
                            requestedSection = section,
                            onRequestConsumed = { section = null },
                            appName = getString(R.string.app_name),
                            appVersion = appVersion,
                            models = catalog,
                            selectedModelId = stored?.let { ModelCatalog.effectiveSelection(catalog, it.id) },
                            onSelectModel = { id -> scope.launch { container.settings.setModelId(id) } },
                            languages = container.language.supported,
                            currentLanguage = language,
                            onSelectLanguage = container.language::set,
                            privacy = privacy,
                            budget = budget,
                            aiProvider = aiProvider,
                            accessibility = AccessibilityUi(
                                isEnabled = accessibilityEnabled,
                                onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            ),
                            debug = if (isDebugBuild) {
                                val captures = remember(debugRefresh) { listDebugCaptures(applicationContext) }
                                DebugUi(
                                    recorderEnabled = privacySettings.fixtureRecorderEnabled,
                                    onRecorderEnabled = { scope.launch { container.privacySettings.setFixtureRecorderEnabled(it) } },
                                    captures = captures,
                                    onClearAll = { clearDebugCaptures(applicationContext); debugRefresh++ },
                                )
                            } else {
                                null
                            },
                        )
                    } else {
                        OnboardingScreen(missing = missing, onRequest = ::request)
                    }
                }
            }
        }
    }

    // Con la app ya abierta, el menu de la burbuja reutiliza esta Activity (CLEAR_TOP + SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        section = AppSection.fromKey(intent.getStringExtra(EXTRA_SECTION))
    }

    override fun onResume() {
        super.onResume()
        // Tambien al volver de los ajustes del sistema tras conceder un permiso.
        refresh()
    }

    private fun refresh() {
        permissions = readPermissions()
        accessibilityEnabled = isAccessibilityServiceEnabled()
        // La lista de Depuracion se calcula una vez por valor de debugRefresh (remember): sin esto, volver
        // a la app tras capturar (sin recrear la Activity) dejaba la lista con lo que hubiera al abrirla.
        debugRefresh++
        // Decidido: abrir la app SIEMPRE arranca la burbuja, esté apagada o no. Es la unica via de
        // volver a encenderla (solo se apaga desde la notificacion).
        if (permissions.allGranted) OverlayService.start(this)
    }

    /**
     * El sistema no ofrece un callback: hay que releer `Settings.Secure` (se hace en cada `onResume`, igual
     * que el resto de permisos). El servicio solo se activa a mano en Ajustes del sistema.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val target = ComponentName(this, LupitaAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.split(':')?.any { it.equals(target, ignoreCase = true) } == true
    }

    private fun readPermissions() = PermissionState(
        overlay = Settings.canDrawOverlays(this),
        notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
    )

    private fun request(permission: RequiredPermission) {
        when (permission) {
            RequiredPermission.OVERLAY -> startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
            RequiredPermission.NOTIFICATIONS -> requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_SECTION = "com.antoniopg.lupita.extra.SECTION"
    }
}
