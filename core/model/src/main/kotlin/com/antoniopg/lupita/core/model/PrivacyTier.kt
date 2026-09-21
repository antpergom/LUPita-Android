package com.antoniopg.lupita.core.model

/**
 * Nivel de sensibilidad de una app. Lo elige el USUARIO; la app solo propone y protege por defecto
 * (ver docs/decisions/2026-09-21-privacidad-de-lo-capturado.md). [key] se persiste: no se renombra.
 */
enum class PrivacyTier(val key: String) {
    /** No se lee el arbol, no se captura la pantalla y no sale nada: solo se genera contexto. */
    PROTECTED("protected"),

    /** Solo la region elegida, con vista previa de lo que se enviara y sin guardar el texto. */
    SENSITIVE("sensitive"),

    /** Solo la region elegida; el resultado se guarda en el historial. */
    NORMAL("normal"),
    ;

    companion object {
        fun fromKey(key: String?): PrivacyTier? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Cada medida de seguridad que el usuario puede activar o desactivar en Ajustes -> Privacidad. Todas
 * vienen activadas: desactivar una es una decision explicita. [key] se persiste: no se renombra.
 *
 * NO estan aqui las que impone el propio sistema y no dependen de LUPita (p. ej. que una ventana con
 * `FLAG_SECURE` no se pueda capturar): ver docs/SEGURIDAD.md.
 */
enum class SecurityMeasure(val key: String, val defaultEnabled: Boolean = true) {
    /** Niveles de sensibilidad por app. Desactivada, todo se trata como «normal». */
    APP_CONTROL("app_control"),

    /** Elimina siempre del arbol los campos de contrasena. */
    DROP_PASSWORD_FIELDS("drop_password_fields"),

    /** En el nivel «sensible», ensena exactamente lo que se enviara y pide confirmacion. */
    PREVIEW_BEFORE_SEND("preview_before_send"),

    /** Sustituye tarjetas, IBAN, correos, telefonos y documentos por marcas antes de enviar o guardar. */
    REDACT_PATTERNS("redact_patterns"),

    /** Registro local (solo metadatos) de lo que ha salido del dispositivo. */
    AUDIT_LOG("audit_log"),

    /** Propone tratar como protegida una app desconocida cuyo nombre parece financiero, etc. */
    PROPOSE_BY_NAME("propose_by_name"),
    ;

    companion object {
        fun fromKey(key: String?): SecurityMeasure? = entries.firstOrNull { it.key == key }
    }
}
