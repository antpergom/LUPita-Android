package com.antoniopg.lupita.capability.image

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun be32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

/** Una caja generica `size(BE32) + tipo(4) + payload`. */
private fun box(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
    val size = 8 + payload.size
    return be32(size) + type.toByteArray(Charsets.US_ASCII) + payload
}

/** Una caja `jumd` con UUID de relleno y, si se da, una etiqueta ASCII+`\0`. */
private fun jumdBox(label: String?): ByteArray {
    val uuid = ByteArray(16) { 0xAB.toByte() }
    val toggle = if (label != null) byteArrayOf(0x01) else byteArrayOf(0x00)
    val labelBytes = label?.let { it.toByteArray(Charsets.UTF_8) + byteArrayOf(0) } ?: ByteArray(0)
    return box("jumd", uuid + toggle + labelBytes)
}

private fun webpWithC2pa(payload: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("RIFF".toByteArray()); out.write(le32(0))
    out.write("WEBP".toByteArray())
    out.write("C2PA".toByteArray()); out.write(le32(payload.size))
    out.write(payload)
    return out.toByteArray()
}

class C2paDetectorTest {

    @Test
    fun `a jumb box with a labelled jumd and one nested manifest box is detected`() {
        val jumd = jumdBox("c2pa")
        val nested = box("c2ma", byteArrayOf(1, 2, 3))
        val outer = box("jumb", jumd + nested)

        val result = C2paDetector.detect(webpWithC2pa(outer))

        assertTrue(result.present)
        assertEquals("c2pa", result.label)
        assertEquals(listOf("c2ma"), result.boxTypes)
    }

    @Test
    fun `several nested boxes are all listed, in order`() {
        val jumd = jumdBox(null)
        val outer = box("jumb", jumd + box("c2cl") + box("c2cs") + box("c2as"))

        val result = C2paDetector.detect(webpWithC2pa(outer))

        assertEquals(listOf("c2cl", "c2cs", "c2as"), result.boxTypes)
        assertNull(result.label)
    }

    @Test
    fun `a WebP with no C2PA chunk is simply absent, not an error`() {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray()); out.write(le32(4))
        out.write("WEBP".toByteArray())

        assertFalse(C2paDetector.detect(out.toByteArray()).present)
    }

    @Test
    fun `bytes that are not a WebP at all are absent, not a crash`() {
        val result = C2paDetector.detect("no soy una imagen".toByteArray())

        assertFalse(result.present)
        assertTrue(result.boxTypes.isEmpty())
    }

    @Test
    fun `a chunk that is not actually a jumb box is reported as absent`() {
        val result = C2paDetector.detect(webpWithC2pa(box("nope", byteArrayOf(1))))

        assertFalse(result.present)
    }

    @Test
    fun `an extended 64-bit box size is recognised as present without guessing its structure`() {
        val fakeExtended = be32(1) + "jumb".toByteArray(Charsets.US_ASCII) + ByteArray(8) // tamano extendido, sin soportar

        val result = C2paDetector.detect(webpWithC2pa(fakeExtended))

        assertTrue(result.present)
        assertNull(result.label)
        assertTrue(result.boxTypes.isEmpty())
    }

    @Test
    fun `truncated or garbage bytes inside the chunk never throw`() {
        assertFalse(C2paDetector.detect(webpWithC2pa(byteArrayOf(1, 2, 3))).present)
    }
}
