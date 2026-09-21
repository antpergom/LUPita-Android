package com.antoniopg.lupita.capability.privacy

/**
 * Sustituye por marcas los datos personales con FORMA reconocible, de forma determinista (sin modelos):
 * tarjetas (Luhn), IBAN (modulo 97), correos, documentos espanoles (letra de control) y telefonos.
 *
 * **Es una red de seguridad, no una garantia.** Un dato sin forma reconocible (un nombre, una direccion,
 * una clave escrita en texto libre) NO se detecta; Microsoft Recall tuvo justo ese problema con su filtro
 * automatico. La defensa principal es el control por app y la vista previa (ver PrivacyGate).
 *
 * Se validan los digitos de control siempre que existen, para no destruir texto util: un numero de 16
 * cifras que no supera Luhn (un identificador cualquiera) se deja como esta, y los precios no se tocan.
 * Aun asi Luhn acierta por coincidencia ~1 de cada 10 secuencias de 13-19 cifras: un falso positivo
 * (redactar de mas) es aceptable en una red de seguridad; lo contrario, no.
 */
object Redactor {

    enum class Kind(val token: String) {
        IBAN("[IBAN]"),
        CARD("[TARJETA]"),
        EMAIL("[CORREO]"),
        DOCUMENT("[DOCUMENTO]"),
        PHONE("[TELEFONO]"),
    }

    data class Result(val text: String, val counts: Map<Kind, Int>) {
        val total: Int get() = counts.values.sum()
    }

    /** Del mas especifico al menos: un IBAN lleva cifras y no debe leerse como telefono ni como tarjeta. */
    private val ORDER = listOf(Kind.IBAN, Kind.CARD, Kind.EMAIL, Kind.DOCUMENT, Kind.PHONE)

    fun redact(text: String, kinds: Set<Kind> = Kind.entries.toSet()): Result {
        var current = text
        val counts = linkedMapOf<Kind, Int>()
        for (kind in ORDER) {
            if (kind !in kinds) continue
            val (redacted, n) = when (kind) {
                Kind.IBAN -> redactIbans(current)
                Kind.CARD -> redactValidated(current, CARD_CANDIDATE, kind) { isValidCard(digitsOf(it)) }
                Kind.EMAIL -> redactValidated(current, EMAIL, kind) { true }
                Kind.DOCUMENT -> redactValidated(current, DOCUMENT, kind, ::isValidDocument)
                Kind.PHONE -> redactPhones(current)
            }
            current = redacted
            if (n > 0) counts[kind] = n
        }
        return Result(current, counts)
    }

    // --- Tarjetas -------------------------------------------------------------------------------
    private val CARD_CANDIDATE = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")

    private fun digitsOf(text: String) = text.filter(Char::isDigit)

    /** Algoritmo de Luhn sobre 13-19 cifras. */
    internal fun isValidCard(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    // --- IBAN -----------------------------------------------------------------------------------
    private val IBAN_CANDIDATE = Regex("""\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{1,4}){2,8}\b""")

    /** Longitud FIJA del IBAN por pais. Con ella se sabe donde acaba, aunque detras haya palabras pegadas. */
    private val IBAN_LENGTHS = mapOf(
        "ES" to 24, "GB" to 22, "DE" to 22, "FR" to 27, "IT" to 27, "PT" to 25, "NL" to 18, "BE" to 16,
        "CH" to 21, "IE" to 22, "AT" to 20, "LU" to 20, "PL" to 28, "SE" to 24, "NO" to 15, "DK" to 18,
        "FI" to 18, "GR" to 27,
    )

    private fun redactIbans(text: String): Pair<String, Int> {
        var count = 0
        val out = IBAN_CANDIDATE.replace(text) { match ->
            val raw = match.value
            val end = ibanEnd(raw)
            if (end == null) {
                raw
            } else {
                count++
                Kind.IBAN.token + raw.substring(end)
            }
        }
        return out to count
    }

    /**
     * Donde termina el IBAN dentro del candidato, o `null` si no hay ninguno valido. El candidato puede
     * arrastrar palabras en mayusculas pegadas al IBAN, y como el resto modulo 97 acierta por
     * COINCIDENCIA una de cada 97 veces, probar «el mas largo que valide» se tragaba la palabra
     * siguiente: por eso, si se conoce el pais, se corta en su longitud fija y solo se valida ahi.
     */
    private fun ibanEnd(raw: String): Int? {
        val expected = IBAN_LENGTHS[raw.take(2)]
        if (expected != null) {
            var seen = 0
            for (i in raw.indices) {
                if (raw[i] != ' ') seen++
                if (seen == expected) return (i + 1).takeIf { isValidIban(raw.substring(0, it)) }
            }
            return null
        }
        // Pais fuera de la tabla: del mas largo al mas corto, recortando por grupos.
        var end = raw.length
        while (end >= 15) {
            if (isValidIban(raw.substring(0, end))) return end
            val cut = raw.lastIndexOf(' ', end - 1)
            if (cut <= 0) return null
            end = cut
        }
        return null
    }

    /** Longitud 15-34 y resto 1 al dividir por 97 tras mover los 4 primeros caracteres al final. */
    internal fun isValidIban(candidate: String): Boolean {
        val iban = candidate.replace(" ", "")
        if (iban.length !in 15..34) return false
        var remainder = 0
        for (c in iban.substring(4) + iban.substring(0, 4)) {
            val value = when {
                c.isDigit() -> c - '0'
                c in 'A'..'Z' -> c - 'A' + 10
                else -> return false
            }
            remainder = if (value < 10) (remainder * 10 + value) % 97 else (remainder * 100 + value) % 97
        }
        return remainder == 1
    }

    // --- Correo ---------------------------------------------------------------------------------
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    // --- DNI / NIE ------------------------------------------------------------------------------
    private val DOCUMENT = Regex("""\b(?:\d{8}|[XYZxyz]\d{7})[A-Za-z]\b""")
    private const val DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

    private fun isValidDocument(text: String): Boolean {
        val upper = text.uppercase()
        val number = when (upper.first()) {
            'X' -> "0" + upper.substring(1, 8)
            'Y' -> "1" + upper.substring(1, 8)
            'Z' -> "2" + upper.substring(1, 8)
            else -> upper.substring(0, 8)
        }
        return DNI_LETTERS[number.toInt() % 23] == upper.last()
    }

    // --- Telefonos ------------------------------------------------------------------------------
    // Solo formas TIPICAS (internacional con +, y movil/fijo espanol de 9 cifras que empieza por 6-9), para
    // no destruir precios ni cantidades.
    private val PHONE_INTERNATIONAL = Regex("""(?<![\w+])\+\d{1,3}[ .-]?\d(?:[ .-]?\d){7,11}(?!\w)""")
    private val PHONE_SPANISH = Regex("""(?<![\w.])[6-9]\d{2}(?:[ .-]?\d{2,3}){2,3}(?![\w])""")

    private fun redactPhones(text: String): Pair<String, Int> {
        var count = 0
        var out = text
        for (regex in listOf(PHONE_INTERNATIONAL, PHONE_SPANISH)) {
            out = regex.replace(out) {
                count++
                Kind.PHONE.token
            }
        }
        return out to count
    }

    private fun redactValidated(text: String, regex: Regex, kind: Kind, isValid: (String) -> Boolean): Pair<String, Int> {
        var count = 0
        val out = regex.replace(text) { match ->
            if (isValid(match.value)) {
                count++
                kind.token
            } else {
                match.value
            }
        }
        return out to count
    }
}
