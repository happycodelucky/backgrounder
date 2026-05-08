package dev.backgrounder.android

import androidx.work.WorkInfo
import dev.backgrounder.ScheduledTask
import dev.backgrounder.TaskId
import kotlin.time.Instant

/**
 * Pure mapping from a single WorkManager [WorkInfo] to a [ScheduledTask] snapshot.
 *
 * Extracted from [AndroidScheduledTaskQuery] so it can be unit-tested in
 * `androidHostTest` without standing up a full `WorkManagerTestInitHelper`
 * harness — `WorkInfo` is constructible from JVM tests, but
 * `WorkManager.getWorkInfosFlow` requires Robolectric. The query stays
 * the I/O-shaped layer; this mapper is the testable transform
 * (review-loop round 1, finding H-CONSENSUS-2).
 *
 * `state` mapping mirrors WorkManager's enum:
 * - `ENQUEUED` with `attempt > 0` → `Backoff`; `attempt == 0` → `Pending`.
 * - `RUNNING` → `Running`.
 * - `BLOCKED` → `Blocked` (Android chained work; v2 in this library).
 * - Terminal states (`SUCCEEDED`, `FAILED`, `CANCELLED`) should never arrive
 *   here — the query filters them out via `WorkQuery` — but if they do, we
 *   defensively map to `Pending` rather than throw.
 */
internal object AndroidScheduledTaskMapper {
    /**
     * Convert a [WorkInfo] to a [ScheduledTask], or return `null` if the
     * info doesn't carry the canonical Backgrounder task-id tag (e.g. the
     * user has unrelated work tagged into the same `WorkManager`).
     *
     * Production entry point — projects [WorkInfo] onto [WorkInfoView] and
     * delegates to [fromView]. Tests should call [fromView] directly with
     * a hand-built [WorkInfoView]; that avoids the verbose `WorkInfo`
     * constructor and lets the mapper be exercised on plain JVM without
     * Robolectric.
     *
     * @param info the WorkManager view of one scheduled item.
     * @param ephemeralIds the snapshot of currently-ephemeral task ids,
     *   used to set the [ScheduledTask.ephemeral] flag.
     */
    fun toScheduledTask(
        info: WorkInfo,
        ephemeralIds: Set<TaskId>,
    ): ScheduledTask? = fromView(WorkInfoView.from(info), ephemeralIds)

    /**
     * Test-friendly mapper: pure function over the primitive fields of
     * [WorkInfo] that this class actually reads. Same logic as
     * [toScheduledTask], but the input is a plain data class so tests don't
     * need to construct a real `WorkInfo`.
     */
    internal fun fromView(
        view: WorkInfoView,
        ephemeralIds: Set<TaskId>,
    ): ScheduledTask? {
        val taskIdString =
            view.tags
                .firstOrNull { it.startsWith(TASK_ID_TAG_PREFIX) }
                ?.removePrefix(TASK_ID_TAG_PREFIX)
                ?: return null
        val taskId = TaskId(taskIdString)
        val isPeriodic = view.tags.contains(KIND_PERIODIC_TAG)
        return ScheduledTask(
            taskId = taskId,
            kind = if (isPeriodic) ScheduledTask.Kind.Periodic else ScheduledTask.Kind.OneTime,
            state = mapState(view.state, view.runAttemptCount),
            nextRunHint =
                if (view.nextScheduleTimeMillis > 0L) {
                    Instant.fromEpochMilliseconds(view.nextScheduleTimeMillis)
                } else {
                    null
                },
            attempt = view.runAttemptCount,
            ephemeral = taskId in ephemeralIds,
        )
    }

    /**
     * Minimal projection of a [WorkInfo] — the four fields [fromView] reads.
     * Lets tests build inputs without constructing a real `WorkInfo`
     * (which has 13+ required constructor parameters and a constructor
     * shape that has shifted across androidx.work minor versions).
     */
    internal data class WorkInfoView(
        val tags: Set<String>,
        val state: WorkInfo.State,
        val runAttemptCount: Int,
        val nextScheduleTimeMillis: Long,
    ) {
        companion object {
            fun from(info: WorkInfo): WorkInfoView =
                WorkInfoView(
                    tags = info.tags,
                    state = info.state,
                    runAttemptCount = info.runAttemptCount,
                    nextScheduleTimeMillis = info.nextScheduleTimeMillis,
                )
        }
    }

    private fun mapState(
        state: WorkInfo.State,
        attempt: Int,
    ): ScheduledTask.State =
        when (state) {
            WorkInfo.State.ENQUEUED -> if (attempt > 0) ScheduledTask.State.Backoff else ScheduledTask.State.Pending

            WorkInfo.State.RUNNING -> ScheduledTask.State.Running

            WorkInfo.State.BLOCKED -> ScheduledTask.State.Blocked

            WorkInfo.State.SUCCEEDED,
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED,
            -> ScheduledTask.State.Pending
        }

    /** The canonical tag every Backgrounder request carries. */
    internal const val BACKGROUNDER_TAG: String = "_backgrounder"

    /** Tag prefix carrying the encoded task id. */
    internal const val TASK_ID_TAG_PREFIX: String = "_backgrounder.id="

    /** Tag stamped on Periodic requests. */
    internal const val KIND_PERIODIC_TAG: String = "_backgrounder.kind=periodic"
}
