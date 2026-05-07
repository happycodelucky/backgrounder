package dev.backgrounder

import kotlin.time.Instant

/**
 * A snapshot of one scheduled task's current state — returned from
 * [Scheduler.scheduled] for inspection.
 *
 * Best-effort per platform:
 * - Android: derived from `WorkInfo` directly.
 * - iOS: combined view of the library's state store and
 *   `BGTaskScheduler.getPendingTaskRequests`. The library does not directly
 *   observe a worker between handler-fire and `setTaskCompletedSuccess`, so
 *   that span is reported as [State.Pending] rather than [State.Running].
 */
public data class ScheduledTask(
    public val taskId: TaskId,
    public val kind: Kind,
    public val state: State,

    /** Best-effort hint of when the platform plans to run this next. May be null. */
    public val nextRunHint: Instant?,

    /** Library-tracked retry attempt counter (within a cycle for periodic). */
    public val attempt: Int,

    public val ephemeral: Boolean,
) {
    public enum class Kind { OneTime, Periodic }

    public enum class State {
        /** Submitted to the platform; waiting for constraints / earliestBeginDate. */
        Pending,

        /** Currently executing (Android only — iOS reports as [Pending] until completion). */
        Running,

        /** Library is waiting on backoff before re-submitting. */
        Backoff,

        /** Awaiting prerequisite work (Android chained work; v2). */
        Blocked,
    }
}
