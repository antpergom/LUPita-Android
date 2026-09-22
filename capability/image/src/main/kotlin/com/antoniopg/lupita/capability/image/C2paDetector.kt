package com.antoniopg.lupita.capability.image

/**
 * Deteccion ESTRUCTURAL de un contenedor C2PA/JUMBF: dice si hay un manifiesto embebido y que tipo de
 * cajas trae por fuera — **nunca** si es autentico. Verificar una firma C2PA de verdad exige comprobar
 * COSE y una cadena de certificados X.509; eso no esta aqui, ni esta previsto para esta version.
 */
data class C2paDetection(val present: Boolean, val label: String? = null, val boxTypes: List<String> = emptyList())

/**
 * Solo WebP (nuestro formato de salida, chunk RIFF `C2PA` conteniendo una caja JUMBF `jumb`/`jumd`; ver
 * ISO/IEC 19566-5). JPEG usa APP11 con el manifiesto repartido en varios segmentos — reensamblarlo con
 * garantias queda fuera de esta primera version, ver docs/decisions/2026-09-22-f3-c2pa-deteccion-estructural.md.
 */
object C2paDetector {
    private const val LABEL_PRESENT = 0x01
    private const val JUMD_HEADER_UUID_TOGGLE = 8 + 16 // caja(8) + UUID(16); el byte de toggle va justo despues

    fun detect(bytes: ByteArray): C2paDetection {
        val jumbf = locateWebpChunk(bytes) ?: return C2paDetection(present = false)
        return runCatching { parseOuterBox(jumbf) }.getOrDefault(C2paDetection(present = false))
    }

    private fun locateWebpChunk(bytes: ByteArray): ByteArray? {
        if (bytes.size <= 12 || !matches(bytes, 0, "RIFF") || !matches(bytes, 8, "WEBP")) return null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = readLE32(bytes, offset + 4)
            val dataStart = offset + 8
            if (size < 0 || dataStart + size > bytes.size) return null
            if (id == "C2PA") return bytes.copyOfRange(dataStart, dataStart + size)
            offset = dataStart + size + (size and 1)
        }
        return null
    }

    /** [jumbf] es la caja `jumb` externa entera (cabecera incluida): el manifiesto de C2PA. */
    private fun parseOuterBox(jumbf: ByteArray): C2paDetection {
        if (jumbf.size < 8) return C2paDetection(present = false)
        val outerSize = readBE32(jumbf, 0)
        val outerType = String(jumbf, 4, 4, Charsets.US_ASCII)
        if (outerType != "jumb") return C2paDetection(present = false)
        // LBox == 1: tamano extendido de 64 bits (Anexo del JUMBF). No se soporta: se sabe que hay algo,
        // pero no se intenta leer su estructura para no leer offsets mal calculados.
        if (outerSize == 1) return C2paDetection(present = true)

        var pos = 8
        if (pos + 8 > jumbf.size) return C2paDetection(present = true)
        val jumdSize = readBE32(jumbf, pos)
        val jumdType = String(jumbf, pos + 4, 4, Charsets.US_ASCII)
        var label: String? = null
        if (jumdType == "jumd" && jumdSize in 8..(jumbf.size - pos)) {
            label = readJumdLabel(jumbf, pos, jumdSize)
            pos += jumdSize
        }

        val end = outerSize.coerceAtMost(jumbf.size)
        val boxTypes = mutableListOf<String>()
        while (pos + 8 <= end) {
            val size = readBE32(jumbf, pos)
            if (size < 8) break
            boxTypes += String(jumbf, pos + 4, 4, Charsets.US_ASCII)
            pos += size
        }
        return C2paDetection(present = true, label = label, boxTypes = boxTypes)
    }

    /** UUID(16) + un byte de banderas +, si la bandera de etiqueta esta puesta, una cadena UTF-8 con `\0` final. */
    private fun readJumdLabel(jumbf: ByteArray, boxStart: Int, boxSize: Int): String? {
        val toggleAt = boxStart + JUMD_HEADER_UUID_TOGGLE
        if (toggleAt >= jumbf.size) return null
        val toggle = jumbf[toggleAt].toInt() and 0xFF
        if (toggle and LABEL_PRESENT == 0) return null
        val labelStart = toggleAt + 1
        val boxEnd = boxStart + boxSize
        val nul = (labelStart until boxEnd.coerceAtMost(jumbf.size)).firstOrNull { jumbf[it] == 0.toByte() } ?: return null
        return String(jumbf, labelStart, nul - labelStart, Charsets.UTF_8)
    }

    private fun matches(bytes: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset < 0 || offset + ascii.length > bytes.size) return false
        return (0 until ascii.length).all { bytes[offset + it] == ascii[it].code.toByte() }
    }

    private fun readLE32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun readBE32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
