package dev.backgrounder.ios

import dev.backgrounder.BackoffPolicy
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * iOS doesn't have native retry/backoff. We emulate by:
 *  - persisting an attempt counter,
 *  - on `Retry`, computing `nextEarliestBeginDate = now + backoff.delayFor(attempt)`,
 *  - resubmitting a fresh `BGTaskRequest` with that date.
 *
 * This module is the pure math; the resubmit lives in [BGTaskBackedScheduler].
 */
internal object IOSBackoffEmulation {

    /** Returns the next `earliestBeginDate` (epoch millis) for this attempt. */
    fun nextRunEpochMs(policy: BackoffPolicy, attempt: Int): Long {
        val delay = policy.delayFor(attempt)
        return Clock.System.now().toEpochMilliseconds() + delay.inWholeMilliseconds
    }

    /** True if [attempt] meets or exceeds the policy's cap. */
    fun shouldGiveUp(policy: BackoffPolicy, attempt: Int): Boolean = attempt >= policy.maxAttempts

    /** Shorthand: epoch millis for "now + d". */
    fun epochMillisAt(d: Duration): Long =
        Clock.System.now().toEpochMilliseconds() + d.inWholeMilliseconds
}
