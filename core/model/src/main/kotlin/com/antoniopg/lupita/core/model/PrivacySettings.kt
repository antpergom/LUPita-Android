package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/** Como se reconoce una app: por paquete exacto o por prefijo (una marca con varias apps). */
sealed interface AppMatch {
    fun matches(packageName: String): Boolean

    /** Forma estable para persistir: `exact:<paquete>` o `prefix:<prefijo>`. */
    val encoded: String

    data class Exact(val packageName: String) : AppMatch {
        override fun matches(packageName: String) = packageName == this.packageName
        override val encoded get() = "exact:$packageName"
    }

    /** Coincide en el LIMITE de paquete: `com.bbva` casa con `com.bbva.app` pero no con `com.bbvax`. */
    data class Prefix(val prefix: String) : AppMatch {
        override fun matches(packageName: String) = packageName == prefix || packageName.startsWith("$prefix.")
        override val encoded get() = "prefix:$prefix"
    }

    companion object {
        /** `null` si el texto no es una regla valida (dato viejo o corrupto): nunca lanza. */
        fun decode(text: String): AppMatch? {
            val kind = text.substringBefore(':', "")
            val value = text.substringAfter(':', "").trim()
            if (value.isEmpty()) return null
            return when (kind) {
                "exact" -> Exact(value)
                "prefix" -> Prefix(value)
                else -> null
            }
        }
    }
}

/** Una excepcion del usuario: esta app (o marca) se trata con este nivel, pase lo que pase en el catalogo. */
data class UserRule(val match: AppMatch, val tier: PrivacyTier) {
    /** `<nivel>|<match>`, p. ej. `protected|exact:com.miBanco`. */
    fun encode(): String = "${tier.key}|${match.encoded}"

    companion object {
        fun decode(text: String): UserRule? {
            val tier = PrivacyTier.fromKey(text.substringBefore('|', "")) ?: return null
            val match = AppMatch.decode(text.substringAfter('|', "")) ?: return null
            return UserRule(match, tier)
        }
    }
}

object PrivacyRegions {
    const val GLOBAL = "GLOBAL"

    /** Regiones activas de fabrica: la global y la del pais del dispositivo (si lo hay). */
    fun defaultFor(country: String?): Set<String> = buildSet {
        add(GLOBAL)
        country?.uppercase()?.takeIf { it.length == 2 }?.let(::add)
    }
}

/**
 * La configuracion de privacidad elegida por el usuario. De fabrica: todas las medidas activas y las
 * apps desconocidas tratadas como SENSIBLES (decision del usuario: nunca «normal» por defecto).
 */
data class PrivacySettings(
    /** Solo las medidas que el usuario ha tocado; las ausentes valen su valor por defecto. */
    val measures: Map<SecurityMeasure, Boolean> = emptyMap(),
    val unknownAppTier: PrivacyTier = PrivacyTier.SENSITIVE,
    /** Nivel elegido por el usuario para un grupo del catalogo (sustituye al que trae el catalogo). */
    val groupTiers: Map<String, PrivacyTier> = emptyMap(),
    val enabledRegions: Set<String> = setOf(PrivacyRegions.GLOBAL),
    val userRules: List<UserRule> = emptyList(),
    /** Si se guarda la imagen enviada (decision 2026-09-22): siempre por defecto. Ver [ImageSavePolicy]. */
    val imageSavePolicy: ImageSavePolicy = ImageSavePolicy.DEFAULT,
) {
    fun isEnabled(measure: SecurityMeasure): Boolean = measures[measure] ?: measure.defaultEnabled
}

/** Contrato de la configuracion de privacidad. La implementacion (DataStore) vive en `:app`. */
interface PrivacySettingsRepository {
    val settings: Flow<PrivacySettings>

    suspend fun setMeasure(measure: SecurityMeasure, enabled: Boolean)

    suspend fun setUnknownAppTier(tier: PrivacyTier)

    /** `null` restablece el nivel del catalogo para ese grupo. */
    suspend fun setGroupTier(groupId: String, tier: PrivacyTier?)

    suspend fun setRegionEnabled(regionId: String, enabled: Boolean)

    /** Anade o sustituye la regla de la misma app. */
    suspend fun putUserRule(rule: UserRule)

    suspend fun removeUserRule(match: AppMatch)

    suspend fun setImageSavePolicy(policy: ImageSavePolicy)
}
