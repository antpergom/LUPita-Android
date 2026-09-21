package com.antoniopg.lupita.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * La imagen que se envia y su preprocesado. Un solo sitio para las constantes: subir [VERSION] cada vez que
 * cambie algo que altere los bytes (tamano, filtro, formato, calidad), para poder comparar en estudios de
 * validacion. Decision 2026-09-22: WebP con perdida, calidad 90 (revisada tras medir: con fondo fotografico el
 * lossless pesaba 4,6 veces mas). Ver docs/decisions/2026-09-22-imagen-enviada-formato-y-almacenamiento.md.
 */
object ImagePipeline {
    const val VERSION = "2"
    const val FORMAT = "webp-lossy"
    const val MIME = "image/webp"
    const val QUALITY = 90
}

/** Lo que paso al recortar y reducir (antes de codificar). Lo rellena la fuente de pantalla. */
@Serializable
data class ImageProvenance(
    /** Tamano de la region en pixeles de pantalla, antes de reducir. */
    val regionWidth: Int,
    val regionHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    /** Espacio de color de la captura original (`null` si no se conoce). Se convierte siempre a sRGB. */
    val sourceColorSpace: String?,
    val resampling: String,
    val maxAreaPx: Long,
) {
    val scale: Double get() = outputWidth.toDouble() / regionWidth
}

/**
 * Registro completo del preprocesado de UNA imagen enviada: es lo que se guardara junto a ella en la base de
 * datos (F4) para comparar variantes en estudios de validacion. Nunca lleva contenido de la imagen.
 */
@Serializable
data class ImageProcessingRecord(
    val pipelineVersion: String,
    val provenance: ImageProvenance?,
    val format: String,
    val mime: String,
    val quality: Int,
    /** Que codificador produjo los bytes (p. ej. `android-bitmap-compress`). */
    val encoder: String,
    val encodedBytes: Int,
    val encodeMs: Long,
    val androidSdk: Int,
    /** Espera entre quitar la capa de captura y pedir la imagen. */
    val settleMs: Long? = null,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { encodeDefaults = true }

        fun fromJson(text: String): ImageProcessingRecord = JSON.decodeFromString(serializer(), text)
    }
}

/** Bytes codificados y el registro de como se obtuvieron. */
class EncodedImage(val bytes: ByteArray, val record: ImageProcessingRecord)
