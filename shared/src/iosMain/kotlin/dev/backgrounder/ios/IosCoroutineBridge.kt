package dev.backgrounder.ios

import co.touchlab.kermit.Logger
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.PlatformCapabilities
import dev.backgrounder.TaskId
import dev.backgrounder.WorkResult
import dev.backgrounder.WorkerContext
import dev.backgrounder.WorkerRegistry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGAppRefreshTask

/**
 * Drives a [BGTask] handler closure from the OS — the load-bearing piece per
 * the plan's "Coroutine bridge" section.
 *
 * The OS calls a Swift / Obj-C closure on its own queue. We launch user code
 * on a [SupervisorJob]-rooted scope (CLAUDE.md §3 forbids `GlobalScope`),
 * wire the task's [BGTask.expirationHandler] to cancel the [Job], and from
 * `invokeOnCompletion` mark the task `setTaskCompletedWithSuccess` only if
 * the worker hadn't already done so itself.
 *
 * Lifecycle of one fire:
 *  1. OS calls the registered closure with a [BGTask].
 *  2. We read attempt + input from [IosStateStore] under the per-id [Mutex].
 *  3. We launch a coroutine that calls the worker.
 *  4. The worker returns a [WorkResult]; the bridge applies it via
 *     [applyResult] (which may resubmit, persist, mark completed).
 *  5. If iOS expires before completion, the expiration handler cancels
 *     the job and reports `setTaskCompletedSuccess(false)`.
 */
internal class IosCoroutineBridge(
    private val registry: WorkerRegistry,
    private val state: IosStateStore,
    private val mutexes: IosTaskMutexes,
    private val eventListener: BackgrounderEventListener,
    private val applyResult: suspend (BGTask, TaskId, Int, WorkResult) -> Unit,
) {

    private val log = Logger.withTag("Backgrounder/iOS")

    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("Backgrounder.iOS"),
    )

    fun handle(task: BGTask, taskId: TaskId) {
        val tagged = log.withTag("Backgrounder/iOS/$taskId")
        // Snapshot attempt/input synchronously (off the main queue) before launching.
        val attempt = state.readAttempt(taskId)
        val input = state.readInput(taskId)
        eventListener.onStarted(taskId, attempt)
        tagged.d { "handle: attempt=$attempt" }

        val capabilities = capabilitiesFor(task)

        val job: Job = scope.launch {
            mutexes.withMutex(taskId) {
                val worker = try {
                    registry.create(taskId)
                } catch (e: WorkerRegistry.NoFactoryRegisteredException) {
                    tagged.e(e) { "no factory registered; treating as Failure" }
                    applyResult(task, taskId, attempt, WorkResult.Failure("no factory: $taskId"))
                    return@withMutex
                }

                val ctx = WorkerContext(
                    taskId = taskId,
                    attempt = attempt,
                    input = input,
                    capabilities = capabilities,
                )

                val result: WorkResult = try {
                    worker.execute(ctx)
                } catch (e: CancellationException) {
                    tagged.i { "cancelled (probably expiration): ${e.message}" }
                    throw e
                } catch (t: Throwable) {
                    tagged.e(t) { "execute() threw; treating as Retry" }
                    WorkResult.Retry
                }

                applyResult(task, taskId, attempt, result)
                eventListener.onCompleted(taskId, attempt, result)
            }
        }

        task.setExpirationHandler {
            tagged.w { "BGTask expired; cancelling worker" }
            job.cancel(CancellationException("BGTask expired"))
        }

        job.invokeOnCompletion { cause ->
            // Success path: applyResult already called setTaskCompleted; nothing to do.
            // Cancellation / unexpected throw path: complete with success=false so iOS
            // doesn't watchdog-terminate us.
            if (cause != null) {
                runCatching { task.setTaskCompletedWithSuccess(false) }
                eventListener.onCompleted(
                    taskId,
                    attempt,
                    WorkResult.Failure(cause.message ?: "expired"),
                )
            }
        }
    }

    private fun capabilitiesFor(task: BGTask): PlatformCapabilities = when (task) {
        // BGAppRefreshTask gets ~30 seconds. Other BGTask subclasses (BGProcessingTask)
        // get "several minutes" — we report a conservative 5-minute hint.
        is BGAppRefreshTask -> PlatformCapabilities(
            maxExecutionTime = 30.seconds,
            cancelsInFlight = false,
        )
        else -> PlatformCapabilities(
            maxExecutionTime = 5.minutes,
            cancelsInFlight = false,
        )
    }
}
