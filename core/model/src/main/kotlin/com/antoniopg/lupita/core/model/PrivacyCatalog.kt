package com.antoniopg.lupita.core.model

/** Un grupo de apps del catalogo (banca, contrasenas, mensajeria...) con su nivel por defecto. */
data class PrivacyGroup(val id: String, val labels: Map<String, String>, val defaultTier: PrivacyTier)

data class PrivacyRegion(val id: String, val labels: Map<String, String>)

/** Una app o marca conocida: a que grupo pertenece y en que regiones aplica. */
data class CatalogEntry(val match: AppMatch, val group: String, val regions: Set<String>)

/**
 * Una palabra que, en el nombre del paquete de una app DESCONOCIDA, solo genera una PROPUESTA
 * («¿tratar como protegida?»); nunca cambia el nivel por si sola.
 */
data class KeywordRule(val word: String, val group: String, val regions: Set<String>)

/**
 * La lista de apps conocidas. Es DATOS (`assets/privacy_catalog.json`), no codigo: anadir una app, un
 * grupo o una region no obliga a tocar nada mas. El usuario la modifica encima con [UserRule] y con
 * [PrivacySettings.groupTiers].
 */
data class PrivacyCatalog(
    val groups: List<PrivacyGroup> = emptyList(),
    val regions: List<PrivacyRegion> = emptyList(),
    val entries: List<CatalogEntry> = emptyList(),
    val keywords: List<KeywordRule> = emptyList(),
) {
    fun group(id: String): PrivacyGroup? = groups.firstOrNull { it.id == id }
}

/** El texto en el idioma pedido; si no existe, en espanol; si tampoco, [fallback]. */
fun Map<String, String>.localized(language: String, fallback: String): String =
    this[language] ?: this["es"] ?: fallback

/** Por que se decidio un nivel. Sirve para explicarselo al usuario y para auditar. */
enum class DecisionSource { SECURE_WINDOW, CONTROL_DISABLED, USER_RULE, CATALOG, UNKNOWN_DEFAULT }

/** Una propuesta por nombre para una app desconocida. No es una decision. */
data class NameSuggestion(val group: String, val tier: PrivacyTier)

data class PrivacyDecision(
    val tier: PrivacyTier,
    val source: DecisionSource,
    val group: String? = null,
    val suggestion: NameSuggestion? = null,
)

/** Lo que el sistema sabe de la ventana que se va a leer, independiente de nuestras listas. */
data class AppSignals(val secureWindow: Boolean = false)
