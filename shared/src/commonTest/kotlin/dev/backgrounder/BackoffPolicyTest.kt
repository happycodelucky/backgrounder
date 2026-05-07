package dev.backgrounder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BackoffPolicyTest {
    @Test
    fun linearGrowsLinearly() {
        val p = BackoffPolicy.Linear(initialDelay = 30.seconds, maxAttempts = 5)
        assertEquals(30.seconds, p.delayFor(0))
        assertEquals(60.seconds, p.delayFor(1))
        assertEquals(90.seconds, p.delayFor(2))
        assertEquals(120.seconds, p.delayFor(3))
    }

    @Test
    fun exponentialGrowsExponentially() {
        val p = BackoffPolicy.Exponential(initialDelay = 30.seconds, maxAttempts = 5)
        assertEquals(30.seconds, p.delayFor(0))
        assertEquals(60.seconds, p.delayFor(1))
        assertEquals(120.seconds, p.delayFor(2))
        assertEquals(240.seconds, p.delayFor(3))
    }

    @Test
    fun exponentialClampsAtMax() {
        // With initialDelay = MIN_BACKOFF and ~2^30 multiplier, MAX_BACKOFF kicks in.
        val p = BackoffPolicy.Exponential(initialDelay = BackoffPolicy.MIN_BACKOFF, maxAttempts = 30)
        val deepDelay = p.delayFor(20)
        assertTrue(deepDelay <= BackoffPolicy.MAX_BACKOFF, "expected $deepDelay <= ${BackoffPolicy.MAX_BACKOFF}")
        assertEquals(BackoffPolicy.MAX_BACKOFF, p.delayFor(30))
    }

    @Test
    fun rejectsTinyInitialDelay() {
        assertFailsWith<IllegalArgumentException> {
            BackoffPolicy.Linear(initialDelay = 1.seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            BackoffPolicy.Exponential(initialDelay = 1.seconds)
        }
    }

    @Test
    fun rejectsZeroOrNegativeMaxAttempts() {
        assertFailsWith<IllegalArgumentException> {
            BackoffPolicy.Linear(initialDelay = 30.seconds, maxAttempts = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BackoffPolicy.Exponential(initialDelay = 30.seconds, maxAttempts = -1)
        }
    }

    @Test
    fun rejectsExcessivelyLargeMaxAttempts() {
        assertFailsWith<IllegalArgumentException> {
            BackoffPolicy.Linear(maxAttempts = BackoffPolicy.MAX_MAX_ATTEMPTS + 1)
        }
    }

    @Test
    fun factoryHelpersReturnExpectedSubtypes() {
        assertTrue(BackoffPolicy.linear() is BackoffPolicy.Linear)
        assertTrue(BackoffPolicy.exponential() is BackoffPolicy.Exponential)
    }
}
