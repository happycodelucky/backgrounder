package dev.backgrounder

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EphemeralRegistryTest {

    private val a = TaskId("dev.backgrounder.test.a")
    private val b = TaskId("dev.backgrounder.test.b")
    private val c = TaskId("dev.backgrounder.test.c")

    @Test
    fun emptyByDefault() {
        val registry = EphemeralRegistry(MapSettings())
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun addAndSnapshotRoundTrip() {
        val registry = EphemeralRegistry(MapSettings())
        registry.add(a)
        registry.add(b)
        assertEquals(setOf(a, b), registry.snapshot())
    }

    @Test
    fun addingDuplicatesIsIdempotent() {
        val registry = EphemeralRegistry(MapSettings())
        registry.add(a)
        registry.add(a)
        registry.add(a)
        assertEquals(setOf(a), registry.snapshot())
    }

    @Test
    fun removeShrinks() {
        val registry = EphemeralRegistry(MapSettings())
        registry.add(a)
        registry.add(b)
        registry.add(c)
        registry.remove(b)
        assertEquals(setOf(a, c), registry.snapshot())
    }

    @Test
    fun clearWipes() {
        val registry = EphemeralRegistry(MapSettings())
        registry.add(a)
        registry.add(b)
        registry.clear()
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun persistsAcrossInstances() {
        val backing = MapSettings()
        EphemeralRegistry(backing).apply {
            add(a)
            add(b)
        }
        // A fresh registry over the same backing storage sees the writes.
        assertEquals(setOf(a, b), EphemeralRegistry(backing).snapshot())
    }
}
