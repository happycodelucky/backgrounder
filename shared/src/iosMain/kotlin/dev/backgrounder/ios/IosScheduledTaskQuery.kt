@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import dev.backgrounder.ScheduledTask
import dev.backgrounder.TaskId
import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler

/**
 * Cross-references the persistent [IosStateStore] with iOS's
 * `BGTaskScheduler.getPendingTaskRequests` to produce [ScheduledTask]
 * snapshots. Best-effort — see plan §iOS step 6 for the state mapping.
 */
internal class IosScheduledTaskQuery(private val state: IosStateStore) {

    suspend fun snapshot(): List<ScheduledTask> {
        val pending: Map<String, BGTaskRequest> = pendingByIdentifier()
        return state.knownTaskIds().mapNotNull { id ->
            val active = state.readActive(id)
            if (!active) return@mapNotNull null
            val kind = when (state.readKind(id)) {
                IosStateStore.Kind.OneShot -> ScheduledTask.Kind.OneTime
                IosStateStore.Kind.Periodic -> ScheduledTask.Kind.Periodic
                null -> return@mapNotNull null
            }
            val attempt = state.readAttempt(id)
            val osPending = pending.containsKey(id.value)
            val state0 = when {
                osPending -> ScheduledTask.State.Pending
                attempt > 0 -> ScheduledTask.State.Backoff
                else -> ScheduledTask.State.Blocked
            }
            ScheduledTask(
                taskId = id,
                kind = kind,
                state = state0,
                nextRunHint = state.readNextRunEpochMs(id)?.let(Instant::fromEpochMilliseconds),
                attempt = attempt,
                ephemeral = state.readEphemeral(id),
            )
        }
    }

    private suspend fun pendingByIdentifier(): Map<String, BGTaskRequest> =
        suspendCancellableCoroutine { cont ->
            BGTaskScheduler.sharedScheduler.getPendingTaskRequestsWithCompletionHandler { list ->
                @Suppress("UNCHECKED_CAST")
                val requests = (list as? List<BGTaskRequest>).orEmpty()
                cont.resume(requests.associateBy { it.identifier })
            }
        }

    @Suppress("unused")
    private fun TaskId.identifierString(): String = value
}
