package com.antoniopg.lupita.capability.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Lo que la pagina dice de si misma, sin interpretarlo: OpenGraph y JSON-LD tal cual vienen. */
data class WebMetadata(
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val imageUrl: String? = null,
    /** Cada bloque `<script type="application/ld+json">`, en crudo (JSON como texto, sin parsear el esquema). */
    val jsonLd: List<String> = emptyList(),
)

data class ReadableContent(val metadata: WebMetadata, val mainText: String)

/**
 * Heuristica de lectura determinista, sin red: dado el HTML (lo trae [WebFetcher]), saca el titulo, la
 * descripcion, la imagen principal y el cuerpo del articulo. «Legible» no significa «cierto» ni «relevante»
 * para lo que se esta verificando — solo que es el texto principal de la pagina.
 */
object Readability {

    private const val MIN_PARAGRAPH_LENGTH = 40

    fun extract(html: String, baseUrl: String = ""): ReadableContent = runCatching {
        val doc = Jsoup.parse(html, baseUrl)
        ReadableContent(metadata = metadataOf(doc), mainText = mainTextOf(doc))
    }.getOrDefault(ReadableContent(WebMetadata(), ""))

    private fun metadataOf(doc: Document): WebMetadata {
        fun meta(property: String) = doc.select("meta[property=$property]").attr("content").ifBlank { null }
        fun metaName(name: String) = doc.select("meta[name=$name]").attr("content").ifBlank { null }
        return WebMetadata(
            title = meta("og:title") ?: doc.title().ifBlank { null },
            description = meta("og:description") ?: metaName("description"),
            siteName = meta("og:site_name"),
            imageUrl = meta("og:image"),
            jsonLd = doc.select("script[type=application/ld+json]").map { it.data() }.filter { it.isNotBlank() },
        )
    }

    /** Quita lo que nunca es el cuerpo del articulo y se queda con los parrafos que parecen prosa de verdad. */
    private fun mainTextOf(doc: Document): String {
        val body = doc.clone()
        body.select("script, style, nav, footer, header, aside, noscript").remove()
        val paragraphs = body.select("p").map { it.text().trim() }.filter { it.length >= MIN_PARAGRAPH_LENGTH }
        return paragraphs.joinToString("\n\n")
    }
}
