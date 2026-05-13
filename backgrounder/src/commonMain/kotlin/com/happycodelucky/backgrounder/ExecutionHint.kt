package com.happycodelucky.backgrounder

import kotlinx.serialization.Serializable

/**
 * A scheduling hint about how the platform should treat a [WorkRequest].
 *
 * Sealed (not a Boolean) because the platforms divide the space differently:
 * Android cares about quota policy when expediting; iOS cares about which
 * `BGTaskRequest` subtype to use.
 *
 * v1 ships [Standard] and [Expedited] only. `LongRunning` (Android
 * `setForeground` + foreground service + notification) is v2.
 */
@Serializable
public sealed interface ExecutionHint {
    /**
     * Default. Android: regular `WorkRequest`. iOS: `BGProcessingTaskRequest`
     * (longer execution window, can require power / network).
     */
    @Serializable public data object Standard : ExecutionHint

    /**
     * Android: `setExpedited(...)` with the given [onQuotaExhausted] policy.
     * iOS: `BGAppRefreshTaskRequest` (short and frequent — typically ~30s
     * budget, ignores [WorkConstraints.requiresCharging]).
     *
     * Pick this when the work needs to start soon and is small.
     */
    @Serializable public data class Expedited(
        val onQuotaExhausted: QuotaPolicy,
    ) : ExecutionHint
}

/**
 * What Android does when expedited quota is exhausted. Maps to
 * `androidx.work.OutOfQuotaPolicy`. iOS ignores it (no quota concept).
 */
public enum class QuotaPolicy {
    /**
     * Fall back to a regular (non-expedited) work request when expedited quota is exhausted.
     * Maps to [androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST].
     */
    RunAsRegular,

    /**
     * Drop the work request entirely when expedited quota is exhausted.
     * Maps to [androidx.work.OutOfQuotaPolicy.DROP_WORK_REQUEST].
     */
    Drop,
}
