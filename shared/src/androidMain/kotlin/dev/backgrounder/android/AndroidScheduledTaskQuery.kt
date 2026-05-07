package dev.backgrounder.android

import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.ScheduledTask
import dev.backgrounder.TaskId
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Instant

/**
 * Translates [WorkInfo] (WorkManager's runtime view) into [ScheduledTask]
 * (the cross-platform snapshot type).
 *
 * Filters by the canonical Backgrounder tag so it doesn't pick up unrelated
 * work the user might have scheduled with the same `WorkManager` instance.
 */
internal class AndroidScheduledTaskQuery(
    private val workManager: WorkManager,
    private val ephemeral: EphemeralRegistry,
) {
    suspend fun snapshot(): List<ScheduledTask> {
        val query = WorkQuery.Builder.fromTags(listOf(BACKGROUNDER_TAG)).build()
        // getWorkInfosFlow emits a current snapshot immediately — we want exactly that.
        val current = workManager.getWorkInfosFlow(query).firstOrNull() ?: return emptyList()
        val ephemeralIds = ephemeral.snapshot()
        return current.mapNotNull { info -> info.toScheduledTask(ephemeralIds) }
    }

    private fun WorkInfo.toScheduledTask(ephemeralIds: Set<TaskId>): ScheduledTask? {
        val taskIdString =
            tags
                .firstOrNull { it.startsWith(TASK_ID_TAG_PREFIX) }
                ?.removePrefix(TASK_ID_TAG_PREFIX)
                ?: return null
        val taskId = TaskId(taskIdString)
        val isPeriodic = tags.contains(KIND_PERIODIC_TAG)
        return ScheduledTask(
            taskId = taskId,
            kind = if (isPeriodic) ScheduledTask.Kind.Periodic else ScheduledTask.Kind.OneTime,
            state = state.toScheduledState(runAttemptCount),
            nextRunHint =
                if (nextScheduleTimeMillis > 0L) {
                    Instant.fromEpochMilliseconds(nextScheduleTimeMillis)
                } else {
                    null
                },
            attempt = runAttemptCount,
            ephemeral = taskId in ephemeralIds,
        )
    }

    private fun WorkInfo.State.toScheduledState(attempt: Int): ScheduledTask.State =
        when (this) {
            WorkInfo.State.ENQUEUED -> {
                if (attempt > 0) ScheduledTask.State.Backoff else ScheduledTask.State.Pending
            }

            WorkInfo.State.RUNNING -> {
                ScheduledTask.State.Running
            }

            WorkInfo.State.BLOCKED -> {
                ScheduledTask.State.Blocked
            }

            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                // Terminal — should have been filtered out by WorkQuery; treat defensively.
                ScheduledTask.State.Pending
            }
        }

    internal companion object {
        /** The canonical tag every Backgrounder request carries. */
        internal const val BACKGROUNDER_TAG: String = "_backgrounder"

        /** Tag prefix carrying the encoded task id. */
        internal const val TASK_ID_TAG_PREFIX: String = "_backgrounder.id="

        /** Tag stamped on Periodic requests. */
        internal const val KIND_PERIODIC_TAG: String = "_backgrounder.kind=periodic"
    }
}
