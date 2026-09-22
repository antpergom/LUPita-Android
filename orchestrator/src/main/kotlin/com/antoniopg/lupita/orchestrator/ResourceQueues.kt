package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.ResourceClass
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Una cola por clase de recurso (arquitectura F4): cada [ResourceClass] tiene su propio limite de tareas en
 * paralelo. Clases DISTINTAS no se bloquean entre si (un LLM lento no frena un hash local); dentro de la
 * MISMA clase, el paralelismo esta acotado para no saturar la conexion ni golpear el limite de tasa de un
 * proveedor de golpe. Nada se fuerza a paralelizar: si el limite de una clase es 1, va en serie.
 */
class ResourceQueues(concurrency: Map<ResourceClass, Int> = DEFAULT_CONCURRENCY) {
    private val semaphores = ResourceClass.entries.associateWith { Semaphore(concurrency[it] ?: 1) }

    suspend fun <T> run(resourceClass: ResourceClass, block: suspend () -> T): T =
        semaphores.getValue(resourceClass).withPermit { block() }

    companion object {
        val DEFAULT_CONCURRENCY: Map<ResourceClass, Int> = mapOf(
            ResourceClass.DETERMINISTIC to 8,
            ResourceClass.ML_LOCAL to 2,
            ResourceClass.NETWORK to 4,
            ResourceClass.LLM to 2,
        )
    }
}
