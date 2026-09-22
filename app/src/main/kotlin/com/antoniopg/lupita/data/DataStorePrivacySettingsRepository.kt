package com.antoniopg.lupita.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.ImageSavePolicy
import com.antoniopg.lupita.core.model.PrivacySettings
import com.antoniopg.lupita.core.model.PrivacySettingsRepository
import com.antoniopg.lupita.core.model.PrivacyTier
import com.antoniopg.lupita.core.model.SecurityMeasure
import com.antoniopg.lupita.core.model.UserRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Configuracion de privacidad en DataStore. Se persisten CLAVES estables. Un dato ilegible NUNCA relaja
 * la proteccion: un nivel corrupto cae a «sensible», y una regla corrupta se ignora.
 *
 * @param defaultRegions regiones activas mientras el usuario no haya tocado ninguna (global + su pais).
 */
class DataStorePrivacySettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaultRegions: Set<String>,
) : PrivacySettingsRepository {

    override val settings: Flow<PrivacySettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            PrivacySettings(
                measures = SecurityMeasure.entries
                    .mapNotNull { m -> prefs[measureKey(m)]?.let { m to it } }
                    .toMap(),
                unknownAppTier = PrivacyTier.fromKey(prefs[UNKNOWN_TIER]) ?: PrivacyTier.SENSITIVE,
                groupTiers = prefs[GROUP_TIERS].orEmpty().mapNotNull(::decodeGroupTier).toMap(),
                enabledRegions = prefs[REGIONS] ?: defaultRegions,
                userRules = prefs[USER_RULES].orEmpty().mapNotNull(UserRule::decode).sortedBy { it.encode() },
                imageSavePolicy = ImageSavePolicy.fromKey(prefs[IMAGE_SAVE_POLICY]) ?: ImageSavePolicy.DEFAULT,
            )
        }

    override suspend fun setMeasure(measure: SecurityMeasure, enabled: Boolean) {
        dataStore.edit { it[measureKey(measure)] = enabled }
    }

    override suspend fun setUnknownAppTier(tier: PrivacyTier) {
        dataStore.edit { it[UNKNOWN_TIER] = tier.key }
    }

    override suspend fun setGroupTier(groupId: String, tier: PrivacyTier?) {
        dataStore.edit { prefs ->
            val others = prefs[GROUP_TIERS].orEmpty().filterNot { decodeGroupTier(it)?.first == groupId }.toSet()
            prefs[GROUP_TIERS] = if (tier == null) others else others + "$groupId=${tier.key}"
        }
    }

    override suspend fun setRegionEnabled(regionId: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[REGIONS] ?: defaultRegions
            prefs[REGIONS] = if (enabled) current + regionId else current - regionId
        }
    }

    override suspend fun putUserRule(rule: UserRule) {
        dataStore.edit { prefs -> writeRules(prefs) { it.filterNot { r -> r.match == rule.match } + rule } }
    }

    override suspend fun removeUserRule(match: AppMatch) {
        dataStore.edit { prefs -> writeRules(prefs) { it.filterNot { r -> r.match == match } } }
    }

    override suspend fun setImageSavePolicy(policy: ImageSavePolicy) {
        dataStore.edit { it[IMAGE_SAVE_POLICY] = policy.key }
    }

    private fun writeRules(prefs: MutablePreferences, change: (List<UserRule>) -> List<UserRule>) {
        val current = prefs[USER_RULES].orEmpty().mapNotNull(UserRule::decode)
        prefs[USER_RULES] = change(current).map { it.encode() }.toSet()
    }

    private fun decodeGroupTier(entry: String): Pair<String, PrivacyTier>? {
        val group = entry.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return null
        val tier = PrivacyTier.fromKey(entry.substringAfter('=', "")) ?: return null
        return group to tier
    }

    private fun measureKey(measure: SecurityMeasure) = booleanPreferencesKey("sec_${measure.key}")

    private companion object {
        val UNKNOWN_TIER = stringPreferencesKey("privacy_unknown_tier")
        val GROUP_TIERS = stringSetPreferencesKey("privacy_group_tiers")
        val REGIONS = stringSetPreferencesKey("privacy_regions")
        val USER_RULES = stringSetPreferencesKey("privacy_user_rules")
        val IMAGE_SAVE_POLICY = stringPreferencesKey("privacy_image_save_policy")
    }
}
