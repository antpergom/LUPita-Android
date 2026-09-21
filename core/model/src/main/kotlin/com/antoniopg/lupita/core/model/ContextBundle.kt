package com.antoniopg.lupita.core.model

/**
 * Cabecera de contexto: lo que se sabe de la pantalla SIN su contenido. Para una app protegida es lo unico
 * que existe («app bancaria, contenido no leido»).
 */
data class ContextHeader(
    val packageName: String,
    val appLabel: String?,
    val tier: PrivacyTier,
    val decisionSource: DecisionSource,
    val group: String?,
    /** `false` si no se ha leido nada (app protegida). */
    val contentRead: Boolean,
    /** Hay campos editables en la region. Sin significado si [contentRead] es `false`. */
    val hasFormFields: Boolean = false,
    /** Nodos cuyo texto la app ha retenido (`accessibilityDataSensitive`). */
    val withheldNodes: Int = 0,
    val droppedPasswordFields: Int = 0,
    /** Cuantas marcas puso el redactor, por tipo (`[TARJETA]` -> 2). */
    val redactions: Map<String, Int> = emptyMap(),
    /** Propuesta por nombre para una app desconocida; nunca una decision. */
    val suggestion: NameSuggestion? = null,
)

/** Lo leido de la region, ya filtrado y redactado. */
class CapturedContent(
    val region: SelectionRect,
    val nodes: List<UiNode>,
    val pixels: PixelBuffer?,
    val provenance: ImageProvenance? = null,
)

/**
 * Resultado de capturar con privacidad: [content] es `null` para una app protegida. [requiresPreview]
 * indica que hay que ensenar al usuario lo que se enviara y pedir confirmacion antes de que salga nada.
 *
 * OJO: los pixeles no se redactan. El redactor solo ve texto: una tarjeta visible en la imagen llega intacta.
 * La defensa es la vista previa y no capturar apps protegidas (ver docs/SEGURIDAD.md).
 */
class ContextBundle(
    val header: ContextHeader,
    val content: CapturedContent?,
    val requiresPreview: Boolean,
)

/**
 * Si se guarda la imagen enviada (decidido 2026-09-22): siempre (por defecto), preguntar antes o nunca.
 * [key] se persiste: no se renombra.
 */
enum class ImageSavePolicy(val key: String) {
    ALWAYS("always"),
    ASK("ask"),
    NEVER("never"),
    ;

    /**
     * La politica que se aplica de verdad segun el nivel de la app: una app protegida nunca guarda, y una
     * sensible pregunta aunque el ajuste global sea «siempre» (para no guardar sin avisar lo que el nivel
     * promete tratar con cuidado). «Nunca» siempre se respeta.
     */
    fun effective(tier: PrivacyTier): ImageSavePolicy = when (tier) {
        PrivacyTier.PROTECTED -> NEVER
        PrivacyTier.SENSITIVE -> if (this == ALWAYS) ASK else this
        PrivacyTier.NORMAL -> this
    }

    companion object {
        val DEFAULT = ALWAYS

        fun fromKey(key: String?): ImageSavePolicy? = entries.firstOrNull { it.key == key }
    }
}
