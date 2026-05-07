// ExperimentalForeignApi: required for cinterop FFI types (NSDate, BGTask, etc.).
// The cinterop surface has been stable across multiple Kotlin releases.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import co.touchlab.kermit.Logger
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.BackoffPolicy
import dev.backgrounder.CancelOutcome
import dev.backgrounder.ConflictPolicy
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.ExecutionHint
import dev.backgrounder.ScheduleOutcome
import dev.backgrounder.ScheduledTask
import dev.backgrounder.Scheduler
import dev.backgrounder.SchedulerGuarantees
import dev.backgrounder.TaskId
import dev.backgrounder.WorkRequest
import dev.backgrounder.WorkResult
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.time.Clock

/**
 * iOS [Scheduler] backed by `BGTaskScheduler`.
 *
 * Periodic semantics are library-emulated — see plan §"iOS implementation"
 * step 2 (state machine). Retry/backoff is also emulated via [IOSStateStore]
 * and [IOSBackoffEmulation].
 */
internal class BGTaskBackedScheduler(
    private val state: IOSStateStore,
    private val mutexes: IOSTaskMutexes,
    private val ephemeral: EphemeralRegistry,
    private val eventListener: BackgrounderEventListener,
) : Scheduler {
    private val log = Logger.withTag("Backgrounder/iOS/Scheduler")

    /** Used by the handler bridge after a worker returns. Wired through the Koin module. */
    internal suspend fun applyResult(
        task: BGTask,
        taskId: TaskId,
        attempt: Int,
        result: WorkResult,
    ) {
        mutexes.withMutex(taskId) {
            state.recordRun(taskId, result)
            val active = state.readActive(taskId)

            when (val kind = state.readKind(taskId)) {
                IOSStateStore.Kind.OneShot -> {
                    handleOneShotResult(task, taskId, attempt, result, active)
                }

                IOSStateStore.Kind.Periodic -> {
                    handlePeriodicResult(task, taskId, attempt, result, active)
                }

                null -> {
                    log.w { "applyResult for unknown task id $taskId; marking iOS task complete" }
                    mutexes.forget(taskId)
                    runCatching { task.setTaskCompletedWithSuccess(result is WorkResult.Success) }
                }
            }
        }
    }

    private fun handleOneShotResult(
        task: BGTask,
        taskId: TaskId,
        attempt: Int,
        result: WorkResult,
        active: Boolean,
    ) {
        when (result) {
            WorkResult.Success, is WorkResult.Failure -> {
                state.setActive(taskId, false)
                state.clear(taskId)
                ephemeral.remove(taskId)
                mutexes.forget(taskId)
                runCatching { task.setTaskCompletedWithSuccess(result is WorkResult.Success) }
            }

            WorkResult.Retry -> {
                if (!active) {
                    log.i { "$taskId one-shot Retry but active=false (cancel won the race); not resubmitting" }
                    runCatching { task.setTaskCompletedWithSuccess(true) }
                    return
                }
                val backoff = backoffPolicyForRetry(taskId)
                val nextAttempt = attempt + 1
                if (IOSBackoffEmulation.shouldGiveUp(backoff, nextAttempt)) {
                    log.w { "$taskId reached maxAttempts(${backoff.maxAttempts}); converting to Failure" }
                    state.setActive(taskId, false)
                    state.clear(taskId)
                    ephemeral.remove(taskId)
                    mutexes.forget(taskId)
                    runCatching { task.setTaskCompletedWithSuccess(false) }
                    return
                }
                state.setAttempt(taskId, nextAttempt)
                // delayFor(attempt) — the attempt that *just failed* — matches Android's
                // WorkManager backoff curve: first retry waits `initialDelay`, second waits
                // `2 * initialDelay` (exponential) or `2 * initialDelay` (linear), etc.
                val nextRun = IOSBackoffEmulation.nextRunEpochMs(backoff, attempt)
                state.setNextRunEpochMs(taskId, nextRun)
                resubmit(taskId, nextRun)
                runCatching { task.setTaskCompletedWithSuccess(true) }
            }
        }
    }

    private fun handlePeriodicResult(
        task: BGTask,
        taskId: TaskId,
        attempt: Int,
        result: WorkResult,
        active: Boolean,
    ) {
        if (!active) {
            log.i { "$taskId periodic but active=false (cancel won the race); not resubmitting" }
            runCatching { task.setTaskCompletedWithSuccess(result is WorkResult.Success) }
            return
        }
        val intervalMs =
            state.readIntervalMs(taskId)
                ?: run {
                    log.e { "$taskId periodic with no interval_ms; bailing out of state machine" }
                    runCatching { task.setTaskCompletedWithSuccess(false) }
                    return
                }
        when (result) {
            WorkResult.Success, is WorkResult.Failure -> {
                state.setAttempt(taskId, 0)
                val nextRun = Clock.System.now().toEpochMilliseconds() + intervalMs
                state.setNextRunEpochMs(taskId, nextRun)
                resubmit(taskId, nextRun)
                runCatching { task.setTaskCompletedWithSuccess(result is WorkResult.Success) }
            }

            WorkResult.Retry -> {
                val backoff = backoffPolicyForRetry(taskId)
                val nextAttempt = attempt + 1
                if (IOSBackoffEmulation.shouldGiveUp(backoff, nextAttempt)) {
                    log.w {
                        "$taskId periodic exhausted maxAttempts(${backoff.maxAttempts}); " +
                            "treating as Failure and resuming regular cadence"
                    }
                    state.setAttempt(taskId, 0)
                    val nextRun = Clock.System.now().toEpochMilliseconds() + intervalMs
                    state.setNextRunEpochMs(taskId, nextRun)
                    resubmit(taskId, nextRun)
                    runCatching { task.setTaskCompletedWithSuccess(false) }
                    return
                }
                state.setAttempt(taskId, nextAttempt)
                // delayFor(attempt) — see one-shot path for rationale.
                val nextRun = IOSBackoffEmulation.nextRunEpochMs(backoff, attempt)
                state.setNextRunEpochMs(taskId, nextRun)
                resubmit(taskId, nextRun)
                runCatching { task.setTaskCompletedWithSuccess(true) }
            }
        }
    }

    /**
     * The resubmit path. We can't recover the original [WorkRequest] (it isn't
     * persisted in full), but we know the kind and can reconstruct the minimal
     * `BGTaskRequest`. The exact `executionHint` isn't preserved — we default
     * to `Standard`, which translates to `BGProcessingTaskRequest`. This is
     * documented in the plan; users who want `Expedited` cadence on iOS
     * shouldn't be using `WorkRequest.Periodic` since it's emulated anyway.
     */
    private fun resubmit(
        taskId: TaskId,
        earliestEpochMs: Long,
    ) {
        val request =
            BGProcessingTaskRequest(taskId.value).apply {
                earliestBeginDate = epochMsToNSDate(earliestEpochMs)
            }
        when (val outcome = submitBGTaskRequest(request)) {
            BGSubmitResult.Success -> Unit
            is BGSubmitResult.Failure -> log.e { "[$taskId] resubmit failed: ${outcome.message}" }
        }
    }

    /**
     * Default backoff policy for retries when we can't recover the original.
     * The user picked it at schedule() time; we don't persist it (yet) so the
     * v1 implementation falls back to a sensible exponential default.
     *
     * v2: persist [BackoffPolicy] alongside the rest of the state.
     */
    private fun backoffPolicyForRetry(taskId: TaskId): BackoffPolicy = BackoffPolicy.exponential()

    // --- Scheduler interface ------------------------------------------------

    override fun schedule(
        request: WorkRequest,
        policy: ConflictPolicy,
    ): ScheduleOutcome {
        if (request.ephemeral) ephemeral.add(request.taskId)
        eventListener.onScheduled(request.taskId, request)

        // If a Keep policy and we already have an active record, no-op.
        if (policy == ConflictPolicy.Keep && state.readActive(request.taskId)) {
            log.d { "schedule(${request.taskId}) Keep: existing active; not resubmitting" }
            return ScheduleOutcome.Scheduled
        }

        return when (request) {
            is WorkRequest.OneTime -> scheduleOneTime(request)
            is WorkRequest.Periodic -> schedulePeriodic(request)
        }
    }

    private fun scheduleOneTime(request: WorkRequest.OneTime): ScheduleOutcome {
        val nextRun = IOSBackoffEmulation.epochMillisAt(request.initialDelay)
        state.writeOnSchedule(
            taskId = request.taskId,
            kind = IOSStateStore.Kind.OneShot,
            input = request.input,
            ephemeral = request.ephemeral,
            intervalMs = null,
            nextRunEpochMs = nextRun,
        )
        val osRequest =
            newOSRequest(request.taskId, request.executionHint).apply {
                earliestBeginDate = epochMsToNSDate(nextRun)
                applyConstraints(request.constraints, request.executionHint)
            }
        return submit(osRequest, request.taskId)
    }

    private fun schedulePeriodic(request: WorkRequest.Periodic): ScheduleOutcome {
        val nextRun = Clock.System.now().toEpochMilliseconds() + request.interval.inWholeMilliseconds
        state.writeOnSchedule(
            taskId = request.taskId,
            kind = IOSStateStore.Kind.Periodic,
            input = request.input,
            ephemeral = request.ephemeral,
            intervalMs = request.interval.inWholeMilliseconds,
            nextRunEpochMs = nextRun,
        )
        val osRequest =
            BGProcessingTaskRequest(request.taskId.value).apply {
                earliestBeginDate = epochMsToNSDate(nextRun)
                applyConstraints(request.constraints, ExecutionHint.Standard)
            }
        return submit(osRequest, request.taskId)
    }

    private fun newOSRequest(
        taskId: TaskId,
        hint: ExecutionHint,
    ): BGTaskRequest =
        when (hint) {
            ExecutionHint.Standard -> BGProcessingTaskRequest(taskId.value)
            is ExecutionHint.Expedited -> BGAppRefreshTaskRequest(taskId.value)
        }

    private fun submit(
        request: BGTaskRequest,
        taskId: TaskId,
    ): ScheduleOutcome =
        when (val outcome = submitBGTaskRequest(request)) {
            BGSubmitResult.Success -> {
                ScheduleOutcome.Scheduled
            }

            is BGSubmitResult.Failure -> {
                log.e { "submit failed for $taskId: ${outcome.message}" }
                // Wipe state so we don't leave a phantom active record.
                state.clear(taskId)
                ScheduleOutcome.Rejected("BGTaskScheduler.submit failed: ${outcome.message}")
            }
        }

    override fun cancel(taskId: TaskId): CancelOutcome {
        val known = state.readKind(taskId) != null
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(taskId.value)
        state.setActive(taskId, false)
        state.clear(taskId)
        ephemeral.remove(taskId)
        mutexes.forget(taskId)
        eventListener.onCancelled(taskId)
        return if (known) CancelOutcome.Cancelled(pendingCleared = 1) else CancelOutcome.NoSuchTask
    }

    override fun cancelAll(): CancelOutcome {
        val ids = state.knownTaskIds()
        if (ids.isEmpty()) {
            BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
            return CancelOutcome.NoSuchTask
        }
        ids.forEach { id ->
            BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(id.value)
            state.clear(id)
            eventListener.onCancelled(id)
        }
        ephemeral.clear()
        mutexes.forgetAll()
        return CancelOutcome.Cancelled(pendingCleared = ids.size)
    }

    override suspend fun scheduled(): List<ScheduledTask> = IOSScheduledTaskQuery(state).snapshot()

    override fun guarantees(): SchedulerGuarantees = IOS_GUARANTEES

    private fun BGProcessingTaskRequest.applyConstraints(
        constraints: dev.backgrounder.WorkConstraints,
        hint: ExecutionHint,
    ) {
        // BGAppRefreshTaskRequest has no constraint fields; only BGProcessingTaskRequest does.
        // Caller already chose the right subclass via newOSRequest. This @apply method only
        // runs when we built a BGProcessingTaskRequest.
        when (constraints.networkRequired) {
            dev.backgrounder.NetworkRequirement.None -> {
                requiresNetworkConnectivity = false
            }

            dev.backgrounder.NetworkRequirement.Any -> {
                requiresNetworkConnectivity = true
            }

            dev.backgrounder.NetworkRequirement.Unmetered -> {
                requiresNetworkConnectivity = true
                log.w {
                    "NetworkRequirement.Unmetered is not honored on iOS; treating as Any. " +
                        "(hint=$hint)"
                }
            }
        }
        requiresExternalPower = constraints.requiresCharging
    }

    /** Helper so BGAppRefreshTaskRequest can also call applyConstraints() — it's a no-op. */
    private fun BGAppRefreshTaskRequest.applyConstraints(
        @Suppress("UNUSED_PARAMETER") constraints: dev.backgrounder.WorkConstraints,
        @Suppress("UNUSED_PARAMETER") hint: ExecutionHint,
    ) {
        // BGAppRefreshTaskRequest doesn't expose constraints. Documented in plan.
    }

    private fun BGTaskRequest.applyConstraints(
        constraints: dev.backgrounder.WorkConstraints,
        hint: ExecutionHint,
    ) {
        when (this) {
            is BGProcessingTaskRequest -> applyConstraints(constraints, hint)
            is BGAppRefreshTaskRequest -> applyConstraints(constraints, hint)
        }
    }
}

/** Convert epoch millis to an NSDate. */
internal fun epochMsToNSDate(epochMs: Long): NSDate {
    val seconds = (epochMs - Clock.System.now().toEpochMilliseconds()) / 1000.0
    return NSDate.dateWithTimeIntervalSinceNow(seconds)
}

private val IOS_GUARANTEES =
    SchedulerGuarantees(
        survivesProcessDeath = true,
        survivesReboot = true,
        survivesForceQuit = false,
        honoursWallClock = false,
        supportsRetryBackoff = true,
        cancelsInFlight = false,
        minimumPeriodicInterval = WorkRequest.MIN_RECOMMENDED_INTERVAL,
        maxConcurrentTasks = 1000,
    )
