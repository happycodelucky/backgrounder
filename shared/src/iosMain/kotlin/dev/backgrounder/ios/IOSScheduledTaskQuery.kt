// ExperimentalForeignApi: required for cinterop FFI types. Stable in practice.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import dev.backgrounder.ScheduledTask
import dev.backgrounder.TaskId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import kotlin.coroutines.resume
import kotlin.time.Instant

/**
 * Cross-references the persistent [IOSStateStore] with iOS's
 * `BGTaskScheduler.getPendingTaskRequests` to produce [ScheduledTask]
 * snapshots. Best-effort — see plan §iOS step 6 for the state mapping.
 */
internal class IOSScheduledTaskQuery(
    private val state: IOSStateStore,
) {
    suspend fun snapshot(): List<ScheduledTask> {
        val pending: Map<String, BGTaskRequest> = pendingByIdentifier()
        return state.knownTaskIds().mapNotNull { id ->
            val active = state.readActive(id)
            if (!active) return@mapNotNull null
            val kind =
                when (state.readKind(id)) {
                    IOSStateStore.Kind.OneShot -> ScheduledTask.Kind.OneTime
                    IOSStateStore.Kind.Periodic -> ScheduledTask.Kind.Periodic
                    null -> return@mapNotNull null
                }
            val attempt = state.readAttempt(id)
            val osPending = pending.containsKey(id.value)
            val state0 =
                when {
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
                // Defensive: K/N stubs the callback as `List<*>?` and the element
                // type isn't checked until access. filterIsInstance drops any
                // stray non-BGTaskRequest entry rather than crashing later.
                val requests = list.orEmpty().filterIsInstance<BGTaskRequest>()
                cont.resume(requests.associateBy { it.identifier })
            }
        }

    @Suppress("unused")
    private fun TaskId.identifierString(): String = value
}
