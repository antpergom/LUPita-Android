package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.core.model.AppMatch
import com.antoniopg.lupita.core.model.CatalogEntry
import com.antoniopg.lupita.core.model.KeywordRule
import com.antoniopg.lupita.core.model.PrivacyCatalog
import com.antoniopg.lupita.core.model.PrivacyGroup
import com.antoniopg.lupita.core.model.PrivacyRegion
import com.antoniopg.lupita.core.model.PrivacyRegions
import com.antoniopg.lupita.core.model.PrivacyTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogFile(
    val groups: List<GroupDto> = emptyList(),
    val regions: List<RegionDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
    val keywords: List<KeywordDto> = emptyList(),
)

@Serializable
private data class GroupDto(val id: String, val label: Map<String, String> = emptyMap(), val defaultTier: String = "sensitive")

@Serializable
private data class RegionDto(val id: String, val label: Map<String, String> = emptyMap())

@Serializable
private data class EntryDto(
    @SerialName("package") val packageName: String? = null,
    val prefix: String? = null,
    val group: String,
    val regions: List<String> = listOf(PrivacyRegions.GLOBAL),
)

@Serializable
private data class KeywordDto(val word: String, val group: String, val regions: List<String> = listOf(PrivacyRegions.GLOBAL))

/**
 * Lee `privacy_catalog.json`. **Falla en blando**: un JSON roto da un catalogo vacio (y las apps
 * quedan como «desconocidas», que es el lado SEGURO), nunca una excepcion que tumbe la app. Una
 * entrada mal formada se descarta sola, sin invalidar las demas.
 */
object PrivacyCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PrivacyCatalog {
        val file = runCatching { json.decodeFromString<CatalogFile>(text) }.getOrNull() ?: return PrivacyCatalog()

        val groups = file.groups
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            // Un nivel ilegible no debe relajar la proteccion: cae a «sensible».
            .map { PrivacyGroup(it.id, it.label, PrivacyTier.fromKey(it.defaultTier) ?: PrivacyTier.SENSITIVE) }
        val groupIds = groups.map { it.id }.toSet()

        val entries = file.entries.mapNotNull { dto ->
            val match = when {
                !dto.packageName.isNullOrBlank() -> AppMatch.Exact(dto.packageName.trim())
                !dto.prefix.isNullOrBlank() -> AppMatch.Prefix(dto.prefix.trim().trimEnd('.'))
                else -> return@mapNotNull null
            }
            if (dto.group !in groupIds) return@mapNotNull null
            CatalogEntry(match, dto.group, dto.regions.toRegionSet())
        }

        val keywords = file.keywords
            .filter { it.word.isNotBlank() && it.group in groupIds }
            .map { KeywordRule(it.word.trim().lowercase(), it.group, it.regions.toRegionSet()) }

        return PrivacyCatalog(
            groups = groups,
            regions = file.regions.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { PrivacyRegion(it.id, it.label) },
            entries = entries,
            keywords = keywords,
        )
    }

    private fun List<String>.toRegionSet(): Set<String> =
        map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet().ifEmpty { setOf(PrivacyRegions.GLOBAL) }
}
