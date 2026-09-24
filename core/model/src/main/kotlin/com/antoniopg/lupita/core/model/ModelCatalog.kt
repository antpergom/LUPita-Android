package com.antoniopg.lupita.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Un modelo de IA elegible en Ajustes. [id] es lo que se persiste; [label] es el nombre del producto.
 * Los tres precios (F5, decision 2026-09-24: OpenAI GPT-6 Luna) son opcionales — `null` en una entrada
 * mock/placeholder que no corresponde a ningun proveedor real todavia; sin ellos no se puede convertir
 * el uso de tokens de esa entrada a [CostMicros], pero el catalogo entero no se descarta por eso.
 */
@Serializable
data class ModelOption(
    val id: String,
    val label: String,
    val inputUsdPerMillion: Double? = null,
    val cachedInputUsdPerMillion: Double? = null,
    val outputUsdPerMillion: Double? = null,
)

@Serializable
private data class ModelCatalogFile(val models: List<ModelOption> = emptyList())

/**
 * El catalogo de modelos vive en un recurso (`assets/model_catalog.json`), no en el codigo: anadir el
 * primer proveedor real (F5) no debe obligar a tocar la pantalla de Ajustes.
 */
object ModelCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Falla en blando: un JSON roto o sin la clave `models` da una lista vacia, nunca una excepcion que
     * tumbe la app. Se descartan entradas sin id o sin etiqueta, y se conserva la primera de cada id.
     */
    fun parse(text: String): List<ModelOption> =
        runCatching { json.decodeFromString<ModelCatalogFile>(text).models }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.id }

    /** El modelo en uso: el guardado si sigue en el catalogo; si no, el primero; `null` si no hay ninguno. */
    fun effectiveSelection(catalog: List<ModelOption>, storedId: String?): String? =
        catalog.firstOrNull { it.id == storedId }?.id ?: catalog.firstOrNull()?.id
}
