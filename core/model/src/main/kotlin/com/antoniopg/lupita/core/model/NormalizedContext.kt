package com.antoniopg.lupita.core.model

/**
 * Rol semantico de un nodo, deducido de forma determinista de su clase y sus banderas — nunca inferido por
 * contenido del texto (eso seria adivinar, ver docs/decisions/2026-09-21-privacidad-de-lo-capturado.md).
 */
enum class NodeRole { HEADING, TEXT, BUTTON, IMAGE, INPUT, LINK, UNKNOWN }

/** Un nodo ya filtrado, con rol y en orden de lectura. Sin los campos de accesibilidad que ya no hacen falta. */
data class NormalizedNode(val role: NodeRole, val text: String?, val bounds: SelectionRect)

/**
 * Deteccion determinista y de baja confianza de la ESTRUCTURA de la region (no del contenido): hoy solo
 * distingue si la forma recuerda a una publicacion de red social (texto + varios contadores pulsables).
 */
enum class ContentPattern { SOCIAL_POST, UNKNOWN }

/**
 * `ContextBundle.content` ya normalizado (F2): sin duplicados de layout, en orden de lectura (arriba-abajo,
 * izquierda-derecha), con rol por nodo. [plainText] es el texto en ese mismo orden, una linea por nodo.
 */
data class NormalizedContext(val nodes: List<NormalizedNode>, val pattern: ContentPattern, val plainText: String)
