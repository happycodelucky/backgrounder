package com.happycodelucky.backgrounder.android

import com.happycodelucky.backgrounder.TaskId
import com.russhwolf.settings.Settings

/**
 * Persisted "an attempt is executing right now" markers, keyed per task id.
 *
 * Process-death contract (D-020/D-022): a run interrupted by process death is
 * dead — the library makes no attempt to restart that transaction. WorkManager's
 * durability model *re-runs* a worker whose process died mid-execution; this
 * store is how the re-run detects that the previous attempt never completed
 * and terminates instead of restarting.
 *
 * Lifecycle: [markStarted] immediately before `BackgroundWorker.execute`,
 * [clear] in a `finally` around it. Every in-process completion path —
 * `Success`, `Failure`, `Retry`, worker threw, WorkManager cancellation —
 * clears the marker; only process death leaves it behind. Work that never
 * started carries no marker and runs normally when WorkManager dispatches it
 * later ("not interrupted — may complete").
 *
 * Per-key Boolean writes only — no read-modify-write, so no lock is needed
 * (contrast [com.happycodelucky.backgrounder.EphemeralRegistry]'s single-key
 * set, which needed one — B-005).
 */
internal class InFlightMarkers(
    private val settings: Settings,
) {
    fun markStarted(taskId: TaskId) {
        settings.putBoolean(key(taskId), true)
    }

    fun clear(taskId: TaskId) {
        settings.remove(key(taskId))
    }

    /** True if a previous attempt for [taskId] died without completing. */
    fun wasInterrupted(taskId: TaskId): Boolean = settings.getBoolean(key(taskId), false)

    private fun key(taskId: TaskId): String = "$KEY_PREFIX${taskId.value}"

    internal companion object {
        internal const val KEY_PREFIX: String = "backgrounder.inflight."
    }
}
