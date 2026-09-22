package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArtifactTest {

    @Test
    fun `the same bytes always hash to the same id`() {
        val bytes = "hola".toByteArray()

        assertEquals(ArtifactHash.of(bytes), ArtifactHash.of(bytes.copyOf()))
    }

    @Test
    fun `different bytes hash to different ids`() {
        assertNotEquals(ArtifactHash.of("hola".toByteArray()), ArtifactHash.of("adios".toByteArray()))
    }

    @Test
    fun `hashing text is the same as hashing its UTF-8 bytes`() {
        assertEquals(ArtifactHash.of("región".toByteArray(Charsets.UTF_8)), ArtifactHash.of("región"))
    }

    @Test
    fun `a hash id is 64 lowercase hex characters`() {
        val id = ArtifactHash.of("cualquier cosa")

        assertEquals(64, id.hex.length)
        assertEquals(id.hex, id.hex.lowercase())
        assertEquals(id.hex, id.toString())
    }

    @Test
    fun `an id with the wrong shape is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ArtifactId("demasiado-corto") }
        assertThrows(IllegalArgumentException::class.java) { ArtifactId("Z".repeat(64)) } // mayuscula, no es hex
    }

    @Test
    fun `an empty artifact still hashes deterministically`() {
        assertEquals(ArtifactHash.of(ByteArray(0)), ArtifactHash.of(ByteArray(0)))
    }
}
