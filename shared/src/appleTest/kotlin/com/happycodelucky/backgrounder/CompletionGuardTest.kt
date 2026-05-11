package com.happycodelucky.backgrounder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reproducer-style tests for the BGTask double-completion guard
 * (review-loop round 1, finding C-1).
 *
 * The actual race lives inside `IOSCoroutineBridge.handle` — a successful
 * worker completion firing `setTaskCompletedWithSuccess(true)` while the
 * iOS expiration handler concurrently fires `setTaskCompletedWithSuccess(false)`.
 * That race can't be reproduced from a unit test without a real `BGTask`,
 * so we test the **invariant** the guard enforces: at-most-once execution
 * of the lambda regardless of how many call sites attempt it.
 */
class CompletionGuardTest {
    @Test
    fun firstRunOnceFiresTheBlock() {
        val guard = CompletionGuard()
        var fired = 0
        val ran = guard.runOnce { fired++ }
        assertTrue(ran, "first runOnce should report true")
        assertEquals(1, fired)
        assertTrue(guard.hasFired)
    }

    @Test
    fun secondRunOnceIsNoOp() {
        val guard = CompletionGuard()
        var fired = 0
        guard.runOnce { fired++ }
        val ranAgain = guard.runOnce { fired++ }
        assertFalse(ranAgain, "second runOnce should report false")
        assertEquals(1, fired, "block must only execute once")
    }

    @Test
    fun differentBlocksDoNotShareGuards() {
        // Each per-BGTask CompletionGuard is independent. A guard fired once for
        // task A doesn't suppress task B's first call.
        val guardA = CompletionGuard()
        val guardB = CompletionGuard()
        var firedA = 0
        var firedB = 0
        guardA.runOnce { firedA++ }
        guardA.runOnce { firedA++ }
        guardB.runOnce { firedB++ }
        assertEquals(1, firedA)
        assertEquals(1, firedB)
    }

    @Test
    fun exceptionInBlockStillMarksGuardAsFired() {
        // If the BGTask completion call itself throws (it won't on iOS, but
        // defending against the contract), the guard must still treat the
        // attempt as having happened so a retry doesn't double-call.
        val guard = CompletionGuard()
        try {
            guard.runOnce { error("simulated cinterop failure") }
        } catch (_: IllegalStateException) {
            // expected
        }
        assertTrue(guard.hasFired, "guard must report fired even if block threw")
        var subsequent = 0
        val ran = guard.runOnce { subsequent++ }
        assertFalse(ran)
        assertEquals(0, subsequent)
    }
}
