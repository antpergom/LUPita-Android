package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.ArtifactId
import java.util.concurrent.ConcurrentHashMap

/**
 * Cachea los bytes de un Artifact por su id (sha256 del contenido, ver Artifact.kt): pedir dos veces la
 * misma capacidad sobre el mismo dato no la vuelve a ejecutar. `InMemoryArtifactCache` es un parche
 * deliberado hasta que Room llegue en el paso 3 de F4 (se pierde al matar el proceso, a proposito: nada
 * critico depende hoy de sobrevivir un reinicio).
 */
interface ArtifactCache {
    fun get(id: ArtifactId): ByteArray?
    fun put(id: ArtifactId, bytes: ByteArray)
}

class InMemoryArtifactCache : ArtifactCache {
    private val store = ConcurrentHashMap<ArtifactId, ByteArray>()

    override fun get(id: ArtifactId): ByteArray? = store[id]

    override fun put(id: ArtifactId, bytes: ByteArray) {
        store[id] = bytes
    }
}
