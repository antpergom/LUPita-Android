package com.antoniopg.lupita.capability.privacy

import com.antoniopg.lupita.capability.privacy.Redactor.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    private fun redact(text: String, kinds: Set<Kind> = Kind.entries.toSet()) = Redactor.redact(text, kinds)

    // --- tarjetas ---
    @Test
    fun `a valid card number is redacted, with or without separators`() {
        assertEquals("pago con [TARJETA] ok", redact("pago con 4111 1111 1111 1111 ok").text)
        assertEquals("[TARJETA]", redact("4111-1111-1111-1111").text)
        assertEquals("[TARJETA]", redact("4111111111111111").text)
    }

    @Test
    fun `a 16 digit number that fails Luhn is left alone - it is just an identifier`() {
        assertEquals("pedido 4111 1111 1111 1112", redact("pedido 4111 1111 1111 1112").text)
    }

    @Test
    fun `luhn accepts real test numbers and rejects a changed digit`() {
        assertTrue(Redactor.isValidCard("4111111111111111"))
        assertTrue(Redactor.isValidCard("378282246310005")) // 15 cifras
        assertFalse(Redactor.isValidCard("4111111111111112"))
        assertFalse(Redactor.isValidCard("411111111111")) // demasiado corto
    }

    // --- IBAN ---
    @Test
    fun `a valid spanish IBAN is redacted`() {
        assertEquals("cuenta [IBAN] gracias", redact("cuenta ES91 2100 0418 4502 0005 1332 gracias").text)
        assertEquals("[IBAN]", redact("ES9121000418450200051332").text)
    }

    @Test
    fun `an IBAN glued to an uppercase word only redacts the IBAN part`() {
        assertEquals("[IBAN] GRACIAS", redact("ES91 2100 0418 4502 0005 1332 GRACIAS").text)
    }

    @Test
    fun `an IBAN glued to letters is cut at the fixed length of its country`() {
        assertEquals("[IBAN]GRACIAS", redact("ES9121000418450200051332GRACIAS").text)
    }

    @Test
    fun `an IBAN of a country outside the length table is still recognised`() {
        assertEquals("cuenta [IBAN]", redact("cuenta SA03 8000 0000 6080 1016 7519").text)
    }

    @Test
    fun `an IBAN with a wrong check digit is not redacted as an IBAN`() {
        // Solo IBAN: si se piden todos los tipos, sus 18 cifras pueden superar Luhn POR COINCIDENCIA y
        // salir como tarjeta (falso positivo aceptable). Aqui se comprueba la validacion del IBAN.
        val wrong = "ES92 2100 0418 4502 0005 1332"
        assertEquals(wrong, redact(wrong, setOf(Kind.IBAN)).text)
    }

    @Test
    fun `iban validation covers other countries`() {
        assertTrue(Redactor.isValidIban("GB82 WEST 1234 5698 7654 32"))
        assertTrue(Redactor.isValidIban("DE89 3704 0044 0532 0130 00"))
        assertFalse(Redactor.isValidIban("GB82 WEST 1234 5698 7654 33"))
    }

    // --- correo ---
    @Test
    fun `emails are redacted`() {
        assertEquals("escribe a [CORREO] hoy", redact("escribe a ana.perez+trabajo@correo.example.es hoy").text)
    }

    // --- documentos ---
    @Test
    fun `a valid DNI and a valid NIE are redacted`() {
        assertEquals("DNI [DOCUMENTO]", redact("DNI 12345678Z").text)
        assertEquals("NIE [DOCUMENTO]", redact("NIE X1234567L").text)
    }

    @Test
    fun `a document with the wrong control letter is not redacted`() {
        assertEquals("12345678A", redact("12345678A").text)
        assertEquals("X1234567A", redact("X1234567A").text)
    }

    // --- telefonos ---
    @Test
    fun `spanish and international phone numbers are redacted`() {
        assertEquals("llama al [TELEFONO]", redact("llama al 612 345 678").text)
        assertEquals("llama al [TELEFONO]", redact("llama al +34 612 34 56 78").text)
        assertEquals("[TELEFONO]", redact("+44 20 7946 0958").text)
    }

    // --- lo que NO se debe tocar ---
    @Test
    fun `prices, dates and quantities are untouched`() {
        val text = "Precio 1.299,99 € el 21/09/2026, 3 unidades, ref 10234"
        assertEquals(text, redact(text).text)
        assertEquals(0, redact(text).total)
    }

    // --- comportamiento general ---
    @Test
    fun `counts say what was redacted`() {
        val result = redact("ES9121000418450200051332 y ana@example.com y 612 345 678")

        assertEquals(mapOf(Kind.IBAN to 1, Kind.EMAIL to 1, Kind.PHONE to 1), result.counts)
        assertEquals(3, result.total)
    }

    @Test
    fun `only the requested kinds are redacted`() {
        val result = redact("ana@example.com y 612 345 678", setOf(Kind.EMAIL))

        assertEquals("[CORREO] y 612 345 678", result.text)
    }

    @Test
    fun `an IBAN is not read as a phone number or a card`() {
        val result = redact("ES9121000418450200051332")

        assertEquals(mapOf(Kind.IBAN to 1), result.counts)
    }

    @Test
    fun `redacting twice gives the same result`() {
        val once = redact("cuenta ES9121000418450200051332 correo ana@example.com").text

        assertEquals(once, redact(once).text)
    }

    @Test
    fun `empty text is fine`() {
        assertEquals("", redact("").text)
    }
}
