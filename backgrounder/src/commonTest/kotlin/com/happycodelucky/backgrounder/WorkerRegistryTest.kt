package com.happycodelucky.backgrounder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class WorkerRegistryTest {
    private val syncId = TaskId("com.happycodelucky.backgrounder.test.sync")
    private val uploadId = TaskId("com.happycodelucky.backgrounder.test.upload")

    // Object expression (not a SAM lambda) so the runtime hands us a *new*
    // instance per invocation — what the registry contract requires.
    private fun newWorker(): BackgroundWorker =
        object : BackgroundWorker {
            override suspend fun execute(context: WorkerContext): WorkResult = WorkResult.Success
        }

    @Test
    fun registerThenCreateReturnsAFreshInstance() {
        val registry = WorkerRegistry()
        registry.register(syncId) { newWorker() }

        val a = registry.create(syncId)
        val b = registry.create(syncId)
        assertNotSame(a, b, "factory should produce a fresh worker per invocation")
    }

    @Test
    fun registeredIdsReflectsRegistrations() {
        val registry = WorkerRegistry()
        registry.register(syncId) { newWorker() }
        registry.register(uploadId) { newWorker() }
        assertEquals(setOf(syncId, uploadId), registry.registeredIds())
    }

    @Test
    fun duplicateRegistrationThrows() {
        val registry = WorkerRegistry()
        registry.register(syncId) { newWorker() }
        assertFailsWith<IllegalArgumentException> {
            registry.register(syncId) { newWorker() }
        }
    }

    @Test
    fun missingFactoryThrows() {
        val registry = WorkerRegistry()
        val missing = TaskId("com.happycodelucky.backgrounder.test.never_registered")
        assertFailsWith<WorkerRegistry.NoFactoryRegisteredException> {
            registry.create(missing)
        }
    }

    @Test
    fun sealRejectsFurtherRegistration() {
        val registry = WorkerRegistry()
        registry.register(syncId) { newWorker() }
        registry.seal()
        assertFailsWith<IllegalStateException> {
            registry.register(uploadId) { newWorker() }
        }
        // But existing factories still work post-seal.
        assertEquals(setOf(syncId), registry.registeredIds())
        registry.create(syncId) // does not throw
    }
}
