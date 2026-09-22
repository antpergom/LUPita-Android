package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.AppSignals
import com.antoniopg.lupita.core.model.CatalogEntry
import com.antoniopg.lupita.core.model.DecisionSource
import com.antoniopg.lupita.core.model.NameSuggestion
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyDecision
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.SecurityMeasure

/**
 * El UNICO punto que decide con que nivel de sensibilidad se trata una app. Se consulta ANTES de
 * capturar nada, y el orquestador no puede saltarselo. Es puro: mismos datos, misma decision.
 *
 * Orden de prioridad (el primero que aplica gana):
 * 1. Ventana protegida por el sistema (`FLAG_SECURE`): siempre PROTEGIDA, ni el usuario lo puede
 *    cambiar (el propio sistema ya rechaza la captura).
 * 2. Control por app desactivado en Ajustes: NORMAL.
 * 3. Regla del USUARIO: la app exacta, y si no, el prefijo mas largo.
 * 4. Catalogo, solo de las regiones activas: la app exacta, y si no, el prefijo mas largo. El nivel es el
 *    que el usuario haya puesto a ese grupo o, si no, el del catalogo.
 * 5. Desconocida: el nivel configurado para desconocidas (SENSIBLE de fabrica), con una propuesta por
 *    nombre si la medida esta activa.
 */
class PrivacyGate(private val catalog: PrivacyCatalog) {

    fun decide(packageName: String, signals: AppSignals, settings: PrivacySettings): PrivacyDecision {
        if (signals.secureWindow) return PrivacyDecision(PrivacyTier.PROTECTED, DecisionSource.SECURE_WINDOW)
        if (!settings.isEnabled(SecurityMeasure.APP_CONTROL)) {
            return PrivacyDecision(PrivacyTier.NORMAL, DecisionSource.CONTROL_DISABLED)
        }

        settings.userRules.mostSpecific(packageName) { it.match }?.let {
            return PrivacyDecision(it.tier, DecisionSource.USER_RULE)
        }

        catalog.entries
            .filter { it.regions.any(settings.enabledRegions::contains) }
            .mostSpecific(packageName, CatalogEntry::match)
            ?.let { return PrivacyDecision(tierOfGroup(it.group, settings), DecisionSource.CATALOG, it.group) }

        return PrivacyDecision(
            tier = settings.unknownAppTier,
            source = DecisionSource.UNKNOWN_DEFAULT,
            suggestion = suggestByName(packageName, settings),
        )
    }

    private fun tierOfGroup(groupId: String, settings: PrivacySettings): PrivacyTier =
        settings.groupTiers[groupId] ?: catalog.group(groupId)?.defaultTier ?: PrivacyTier.SENSITIVE

    /** Solo una PROPUESTA: separa el paquete en trozos y busca palabras del catalogo dentro de ellos. */
    private fun suggestByName(packageName: String, settings: PrivacySettings): NameSuggestion? {
        if (!settings.isEnabled(SecurityMeasure.PROPOSE_BY_NAME)) return null
        if (packageName in settings.dismissedSuggestions) return null
        val parts = packageName.lowercase().split('.', '_', '-')
        val rule = catalog.keywords.firstOrNull { keyword ->
            keyword.regions.any(settings.enabledRegions::contains) && parts.any { it.contains(keyword.word) }
        } ?: return null
        return NameSuggestion(rule.group, tierOfGroup(rule.group, settings))
    }

    /** La coincidencia mas especifica: una app exacta gana a cualquier prefijo, y entre prefijos, el mas largo. */
    private fun <T> List<T>.mostSpecific(packageName: String, match: (T) -> AppMatch): T? =
        filter { match(it).matches(packageName) }.maxByOrNull { specificity(match(it)) }

    private fun specificity(match: AppMatch): Int = when (match) {
        is AppMatch.Exact -> Int.MAX_VALUE
        is AppMatch.Prefix -> match.prefix.length
    }
}
