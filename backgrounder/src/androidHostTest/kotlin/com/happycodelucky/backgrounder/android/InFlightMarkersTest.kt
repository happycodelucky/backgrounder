package com.happycodelucky.backgrounder.android

import com.happycodelucky.backgrounder.TaskId
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Anchor for the process-death contract (D-020/D-022): the marker is set
 * around `execute()` and cleared on every in-process completion path; a
 * marker found at the *next* run means the previous attempt died with its
 * process — that run terminates instead of restarting the transaction.
 */
class InFlightMarkersTest {
    private val a = TaskId("com.happycodelucky.backgrounder.test.a")
    private val b = TaskId("com.happycodelucky.backgrounder.test.b")

    @Test
    fun markerLifecycleDetectsDeathInterruptedRun() {
        val markers = InFlightMarkers(MapSettings())

        assertFalse(markers.wasInterrupted(a), "fresh id: never started, not interrupted")

        // Normal run: marked, then cleared by the finally — next run sees clean state.
        markers.markStarted(a)
        markers.clear(a)
        assertFalse(markers.wasInterrupted(a), "completed run leaves no marker")

        // Death-interrupted run: marked, never cleared (process died).
        markers.markStarted(a)
        assertTrue(markers.wasInterrupted(a), "uncleared marker = previous attempt died")
        markers.clear(a)
        assertFalse(markers.wasInterrupted(a), "the suppressed re-run clears the marker")
    }

    @Test
    fun markersArePerTaskId() {
        val markers = InFlightMarkers(MapSettings())
        markers.markStarted(a)
        assertTrue(markers.wasInterrupted(a))
        assertFalse(markers.wasInterrupted(b), "marker for one id must not bleed into another")
    }
}
