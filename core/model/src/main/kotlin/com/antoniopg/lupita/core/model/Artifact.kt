package com.antoniopg.lupita.core.model

import java.security.MessageDigest

/**
 * Direccionado por hash de contenido (SHA-256): el mismo contenido produce siempre el mismo id, asi que
 * dos herramientas que piden lo mismo lo comparten sin que el orquestador (F4) tenga que saberlo de
 * antemano. `java.security.MessageDigest` es JVM puro, no `android.*`.
 */
data class ArtifactId(val hex: String) {
    init {
        require(hex.length == 64 && hex.all { it.isDigit() || it in 'a'..'f' }) { "hash SHA-256 invalido: $hex" }
    }

    override fun toString(): String = hex
}

enum class ArtifactKind { IMAGE, PLAIN_TEXT, NORMALIZED_CONTEXT }

/** Metadatos de un artefacto; nunca lleva los bytes (esos van donde corresponda: memoria, Room en F4...). */
data class Artifact(
    val id: ArtifactId,
    val kind: ArtifactKind,
    val mimeType: String,
    val sizeBytes: Int,
    val createdAtMillis: Long,
)

object ArtifactHash {
    fun of(bytes: ByteArray): ArtifactId {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return ArtifactId(digest.joinToString("") { "%02x".format(it) })
    }

    fun of(text: String): ArtifactId = of(text.toByteArray(Charsets.UTF_8))
}
