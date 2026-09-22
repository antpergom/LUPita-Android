package com.antoniopg.lupita.capability.context

import com.antoniopg.lupita.core.model.ContentPattern
import com.antoniopg.lupita.core.model.NodeRole
import com.antoniopg.lupita.core.model.NormalizedContext
import com.antoniopg.lupita.core.model.NormalizedNode
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.UiNode

/**
 * F2: del arbol de accesibilidad ya filtrado por region (F1.3) a una lista plana, sin envoltorios de
 * layout, en orden de lectura y con un rol por nodo — determinista, sin ningun modelo de por medio.
 */
object ContextNormalizer {

    /** Contador de mas de un digito seguido, opcionalmente con un punto/coma y un sufijo K/M (`1.2K`, `348`). */
    private val COUNTER = Regex("""^\d+([.,]\d+)?[KMk]?$""")
    private val URL_LIKE = Regex("""^(https?://|www\.)""", RegexOption.IGNORE_CASE)

    fun normalize(roots: List<UiNode>): NormalizedContext {
        val flat = roots.flatMap { it.flatten() }
        val meaningful = flat.filter(::isMeaningful)
        val deduped = dropDuplicates(meaningful)
        val ordered = deduped.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val nodes = ordered.map { NormalizedNode(roleOf(it), textOf(it), it.bounds) }
        return NormalizedContext(
            nodes = nodes,
            pattern = detectPattern(nodes),
            plainText = nodes.mapNotNull { it.text }.joinToString("\n"),
        )
    }

    /** Sin area no hay nada que mostrar; sin texto, descripcion, foco de entrada o toque, es un envoltorio de layout. */
    private fun isMeaningful(node: UiNode): Boolean {
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return false
        return !node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank() ||
            node.isClickable ||
            node.isEditable ||
            node.contentWithheld
    }

    /** Un padre y un hijo con las mismas coordenadas y el mismo texto son el mismo contenido dos veces. */
    private fun dropDuplicates(nodes: List<UiNode>): List<UiNode> {
        val seen = HashSet<Pair<SelectionRect, String?>>()
        return nodes.filter { seen.add(it.bounds to textOf(it)) }
    }

    private fun textOf(node: UiNode): String? = node.text?.takeIf { it.isNotBlank() } ?: node.contentDescription?.takeIf { it.isNotBlank() }

    private fun roleOf(node: UiNode): NodeRole {
        val cls = node.className.orEmpty()
        return when {
            node.isEditable -> NodeRole.INPUT
            cls.contains("Button", ignoreCase = true) -> NodeRole.BUTTON
            cls.contains("Image", ignoreCase = true) || cls.contains("Icon", ignoreCase = true) -> NodeRole.IMAGE
            cls.contains("Heading", ignoreCase = true) -> NodeRole.HEADING
            node.isClickable && textOf(node)?.let(URL_LIKE::containsMatchIn) == true -> NodeRole.LINK
            textOf(node) != null -> NodeRole.TEXT
            else -> NodeRole.UNKNOWN
        }
    }

    /**
     * Heuristica de baja confianza, de primera version: dos o mas numeros cortos (tipicos de contadores de
     * «me gusta»/«comentarios»/«compartir») junto a un texto mas largo sugieren una publicacion. Falla
     * hacia UNKNOWN: nunca afirma un patron sin al menos dos senales, y un texto corto no basta.
     */
    private fun detectPattern(nodes: List<NormalizedNode>): ContentPattern {
        val counters = nodes.count { it.text?.let(COUNTER::matches) == true }
        val hasBody = nodes.any { (it.text?.length ?: 0) > 20 }
        return if (counters >= 2 && hasBody) ContentPattern.SOCIAL_POST else ContentPattern.UNKNOWN
    }
}
