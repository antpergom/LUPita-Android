package com.antoniopg.lupita.core.model

/**
 * Una propuesta por nombre (ver `PrivacyGate.suggestByName`) que quedo pendiente de aceptar o descartar, para
 * no recalcularla ni repetirla en cada captura de la misma app.
 */
data class PendingSuggestion(val packageName: String, val group: String, val tier: PrivacyTier) {
    /** `<paquete>|<grupo>|<nivel>`. El paquete nunca lleva `|` (formato de Android). */
    fun encode(): String = "$packageName|$group|${tier.key}"

    companion object {
        fun decode(raw: String): PendingSuggestion? {
            val parts = raw.split('|')
            if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null
            val tier = PrivacyTier.fromKey(parts[2]) ?: return null
            return PendingSuggestion(parts[0], parts[1], tier)
        }
    }
}

enum class AuditOutcome(val key: String) {
    /** Se leyo la region (nodos y/o imagen). */
    READ("read"),

    /** App protegida: no se leyo nada. */
    PROTECTED("protected"),
    ;

    companion object {
        fun fromKey(key: String?): AuditOutcome? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Una entrada del registro de auditoria: SOLO metadatos (hora, app, nivel, resultado, tamano), nunca texto ni
 * imagen — ver `docs/SEGURIDAD.md`. Hoy registra el resultado de leer la region; cuando exista el envio a un
 * proveedor (F5) se anadira una entrada propia para eso.
 */
data class AuditEntry(
    val timestampMillis: Long,
    val packageName: String,
    val tier: PrivacyTier,
    val outcome: AuditOutcome,
    val kilobytes: Int,
) {
    fun encode(): String = listOf(timestampMillis, packageName, tier.key, outcome.key, kilobytes).joinToString("|")

    companion object {
        fun decode(raw: String): AuditEntry? {
            val parts = raw.split('|')
            if (parts.size != 5) return null
            val ts = parts[0].toLongOrNull() ?: return null
            val tier = PrivacyTier.fromKey(parts[2]) ?: return null
            val outcome = AuditOutcome.fromKey(parts[3]) ?: return null
            val kb = parts[4].toIntOrNull() ?: return null
            return AuditEntry(ts, parts[1], tier, outcome, kb)
        }
    }
}
