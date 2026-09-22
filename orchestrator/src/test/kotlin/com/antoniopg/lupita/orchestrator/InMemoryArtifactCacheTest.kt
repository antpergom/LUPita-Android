package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.ArtifactHash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryArtifactCacheTest {
    @Test
    fun `a miss returns null`() {
        val cache = InMemoryArtifactCache()
        assertNull(cache.get(ArtifactHash.of("nunca guardado")))
    }

    @Test
    fun `what is put can be read back by the same id`() {
        val cache = InMemoryArtifactCache()
        val id = ArtifactHash.of("hola")
        val bytes = "hola".toByteArray()

        cache.put(id, bytes)

        assertArrayEquals(bytes, cache.get(id))
    }

    @Test
    fun `overwriting the same id replaces the previous bytes`() {
        val cache = InMemoryArtifactCache()
        val id = ArtifactHash.of("clave")

        cache.put(id, "primero".toByteArray())
        cache.put(id, "segundo".toByteArray())

        assertArrayEquals("segundo".toByteArray(), cache.get(id))
    }
}
