package com.happycodelucky.backgrounder.ios

import com.happycodelucky.backgrounder.BackoffPolicy
import kotlin.time.Duration

/**
 * iOS doesn't have native retry/backoff. We emulate by:
 *  - persisting an attempt counter,
 *  - on `Retry`, computing `nextEarliestBeginDate = now + backoff.delayFor(attempt)`,
 *  - resubmitting a fresh `BGTaskRequest` with that date.
 *
 * This module is the pure math; the resubmit lives in [BGTaskBackedScheduler].
 *
 * Pure math means **no wall-clock reads** (N-011 / B-020): callers pass their
 * injected `clock()` value as [nowMs], so `runTest` virtual time reaches every
 * timestamp this object produces.
 */
internal object IOSBackoffEmulation {
    /** Returns the next `earliestBeginDate` (epoch millis) for this attempt. */
    fun nextRunEpochMs(
        policy: BackoffPolicy,
        attempt: Int,
        nowMs: Long,
    ): Long {
        val delay = policy.delayFor(attempt)
        return nowMs + delay.inWholeMilliseconds
    }

    /** True if [attempt] meets or exceeds the policy's cap. */
    fun shouldGiveUp(
        policy: BackoffPolicy,
        attempt: Int,
    ): Boolean = attempt >= policy.maxAttempts

    /** Shorthand: epoch millis for "now + d". */
    fun epochMillisAt(
        d: Duration,
        nowMs: Long,
    ): Long = nowMs + d.inWholeMilliseconds
}
