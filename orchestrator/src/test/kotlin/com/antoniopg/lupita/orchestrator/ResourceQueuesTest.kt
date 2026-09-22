package com.antoniopg.lupita.orchestrator

import com.antoniopg.lupita.core.model.ResourceClass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ResourceQueuesTest {
    @Test
    fun `runs a single task and returns its result`() = runTest {
        val queues = ResourceQueues()
        val result = queues.run(ResourceClass.DETERMINISTIC) { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `caps concurrency at the configured limit for that resource class`() = runBlocking {
        val queues = ResourceQueues(mapOf(ResourceClass.LLM to 2))
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val jobs = (1..5).map {
            async {
                queues.run(ResourceClass.LLM) {
                    val now = running.incrementAndGet()
                    maxSeen.updateAndGet { prev -> maxOf(prev, now) }
                    gate.await()
                    running.decrementAndGet()
                }
            }
        }
        // Deja que las tareas permitidas por el semaforo entren a la seccion critica antes de liberarlas.
        while (running.get() < 2) { yield() }
        assertTrue("nunca deberian correr mas de 2 LLM a la vez, se vieron ${maxSeen.get()}", maxSeen.get() <= 2)
        gate.complete(Unit)
        jobs.forEach { it.await() }
    }

    @Test
    fun `different resource classes do not block each other`() = runTest {
        val queues = ResourceQueues(mapOf(ResourceClass.LLM to 1))
        val gate = CompletableDeferred<Unit>()
        val llmJob = async {
            queues.run(ResourceClass.LLM) { gate.await() }
        }
        // Si compartieran cola, esto se bloquearia esperando al LLM de arriba.
        val deterministicResult = queues.run(ResourceClass.DETERMINISTIC) { "libre" }
        assertEquals("libre", deterministicResult)
        gate.complete(Unit)
        llmJob.await()
    }
}
