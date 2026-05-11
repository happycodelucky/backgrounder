// ExperimentalForeignApi: required for cinterop FFI types
// (NSBackgroundActivityScheduler, NSBackgroundActivityResult). Stable in practice.
@file:OptIn(ExperimentalForeignApi::class)

package com.happycodelucky.backgrounder.macos

import co.touchlab.kermit.Logger
import com.happycodelucky.backgrounder.BackgrounderEventListener
import com.happycodelucky.backgrounder.CancelOutcome
import com.happycodelucky.backgrounder.ConflictPolicy
import com.happycodelucky.backgrounder.EphemeralRegistry
import com.happycodelucky.backgrounder.PlatformCapabilities
import com.happycodelucky.backgrounder.ScheduleOutcome
import com.happycodelucky.backgrounder.ScheduledTask
import com.happycodelucky.backgrounder.Scheduler
import com.happycodelucky.backgrounder.SchedulerGuarantees
import com.happycodelucky.backgrounder.TaskId
import com.happycodelucky.backgrounder.WorkRequest
import com.happycodelucky.backgrounder.WorkResult
import com.happycodelucky.backgrounder.WorkerContext
import com.happycodelucky.backgrounder.WorkerRegistry
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSBackgroundActivityResultDeferred
import platform.Foundation.NSBackgroundActivityResultFinished
import platform.Foundation.NSBackgroundActivityScheduler
import platform.Foundation.NSQualityOfServiceUtility
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * macOS [Scheduler] backed by Foundation's `NSBackgroundActivityScheduler`.
 *
 * Closer in spirit to WorkManager than `BGTaskScheduler` is — repeats are
 * native, scheduling lives in-process (no per-cold-launch handler
 * registration), and the scheduler invalidates cleanly. Hence we don't need
 * the BGTaskScheduler-style state store / resurrection / per-id mutex
 * machinery from iOS.
 */
internal class NSBackgroundActivityBackedScheduler(
    private val registry: WorkerRegistry,
    private val ephemeral: EphemeralRegistry,
    private val eventListener: BackgrounderEventListener,
) : Scheduler {
    private val log = Logger.withTag("Backgrounder/macOS/Scheduler")

    private val scope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineName("Backgrounder.macOS"),
        )

    /**
     * Live registry keyed by task id. Owned by this scheduler instance.
     * MUST NOT call suspend functions inside `synchronized(lock) { ... }` blocks
     * — the `Scheduler` interface methods are non-suspending by design (see
     * CLAUDE.md §3 and Scheduler KDoc).
     */
    private val lock = SynchronizedObject()
    private val activities: MutableMap<TaskId, NSBackgroundActivityScheduler> = mutableMapOf()
    private val attempts: MutableMap<TaskId, Int> = mutableMapOf()

    /**
     * Track each scheduled request's [ScheduledTask.Kind] so [scheduled] can
     * report it accurately (NSBackgroundActivityScheduler exposes `repeats` but
     * not via a public Kotlin/Native API — easier to track ourselves).
     */
    private val kinds: MutableMap<TaskId, ScheduledTask.Kind> = mutableMapOf()

    override fun schedule(
        request: WorkRequest,
        policy: ConflictPolicy,
    ): ScheduleOutcome {
        // H-3 (parallel to iOS): the Keep-policy early-return must NOT run side
        // effects. Probe the activities map first (under the lock to avoid a
        // half-released-then-reacquired window), and only fire `onScheduled` /
        // mutate `ephemeral` if we are actually going to schedule.
        val keepWonRace =
            synchronized(lock) {
                val existing = activities[request.taskId]
                policy == ConflictPolicy.Keep && existing != null
            }
        if (keepWonRace) {
            log.d { "schedule(${request.taskId}) Keep: existing activity; not replacing" }
            return ScheduleOutcome.Scheduled
        }

        if (request.ephemeral) ephemeral.add(request.taskId)
        eventListener.onScheduled(request.taskId, request)

        return synchronized(lock) {
            // Re-check under the lock — a concurrent `schedule(...)` may have raced
            // in between the probe above and this acquisition. If we lose the race,
            // honour Keep semantics for the task that won.
            val existing = activities[request.taskId]
            if (existing != null && policy == ConflictPolicy.Keep) {
                log.d { "schedule(${request.taskId}) Keep: existing activity; not replacing (lock-recheck)" }
                return@synchronized ScheduleOutcome.Scheduled
            }
            existing?.invalidate()

            val activity =
                NSBackgroundActivityScheduler(request.taskId.value).apply {
                    qualityOfService = NSQualityOfServiceUtility
                    when (request) {
                        is WorkRequest.OneTime -> {
                            repeats = false
                            // For one-shots, `interval` is the minimum delay before the
                            // single fire. Allow zero — `WorkRequest.OneTime.initialDelay`
                            // can be `Duration.ZERO`. Foundation's contract is tolerance
                            // <= interval, so cap tolerance at the (possibly zero) interval.
                            interval = (request.initialDelay.inWholeMilliseconds / 1000.0).coerceAtLeast(0.0)
                            tolerance = (interval * 0.1).coerceAtMost(interval)
                        }

                        is WorkRequest.Periodic -> {
                            repeats = true
                            interval = (request.interval.inWholeMilliseconds / 1000.0).coerceAtLeast(1.0)
                            // Tolerance must be <= interval; the explicit flexWindow is also
                            // capped here so a misconfigured flex doesn't violate the contract.
                            val rawTolerance =
                                request.flexWindow?.inWholeMilliseconds?.let { it / 1000.0 }
                                    ?: (interval * 0.1).coerceAtLeast(60.0)
                            tolerance = rawTolerance.coerceAtMost(interval)
                        }
                    }
                }

            activities[request.taskId] = activity
            attempts[request.taskId] = 0
            kinds[request.taskId] =
                when (request) {
                    is WorkRequest.OneTime -> ScheduledTask.Kind.OneTime
                    is WorkRequest.Periodic -> ScheduledTask.Kind.Periodic
                }
            launchActivity(activity, request)
            ScheduleOutcome.Scheduled
        }
    }

    private fun launchActivity(
        activity: NSBackgroundActivityScheduler,
        request: WorkRequest,
    ) {
        activity.scheduleWithBlock { completion ->
            // The closure runs on a system queue. Bounce into a coroutine for
            // suspending user code, then call the completion handler with the
            // appropriate NSBackgroundActivityResult.
            scope.launch {
                val attempt = synchronized(lock) { attempts[request.taskId] ?: 0 }
                eventListener.onStarted(request.taskId, attempt)

                val worker =
                    try {
                        registry.create(request.taskId)
                    } catch (e: WorkerRegistry.NoFactoryRegisteredException) {
                        log.e(e) { "[${request.taskId}] no factory registered" }
                        completion?.invoke(NSBackgroundActivityResultFinished)
                        return@launch
                    }

                val ctx =
                    WorkerContext(
                        taskId = request.taskId,
                        attempt = attempt,
                        input = request.input,
                        capabilities =
                            PlatformCapabilities(
                                // NSBackgroundActivityScheduler is generous; CTS gives macOS
                                // jobs minutes-to-hours of real wall-clock budget under load.
                                maxExecutionTime = 5.minutes,
                                cancelsInFlight = true,
                            ),
                    )

                val result =
                    try {
                        worker.execute(ctx)
                    } catch (e: CancellationException) {
                        log.i { "[${request.taskId}] cancelled" }
                        throw e
                    } catch (t: Throwable) {
                        log.e(t) { "[${request.taskId}] threw; treating as Retry" }
                        WorkResult.Retry
                    }

                eventListener.onCompleted(request.taskId, attempt, result)

                synchronized(lock) {
                    attempts[request.taskId] =
                        when (result) {
                            WorkResult.Success, is WorkResult.Failure -> 0
                            WorkResult.Retry -> attempt + 1
                        }
                }

                // Periodic Retry: NSBackgroundActivityResultDeferred means "run me again
                // later" and the OS will re-fire per the activity's `interval`. Periodic
                // Success / Failure is also handled by the OS — Foundation will schedule
                // the next cycle automatically.
                //
                // One-shot Retry — H-7 (review-loop round 1): a one-shot activity has
                // `repeats=false`, and Apple documents that NSBackgroundActivityResultDeferred
                // has no effect on a non-repeating activity. The previous code returned
                // Deferred for this case, which silently dropped the Retry. The honest fix
                // is to invalidate the current activity, schedule a fresh one with
                // `interval = backoff.delayFor(attempt)`, and report Finished for the
                // current attempt. Mirrors the iOS scheduler's resubmit pattern.
                val activityResult =
                    if (result is WorkResult.Retry && request is WorkRequest.OneTime) {
                        handleOneShotRetry(request, attempt)
                    } else {
                        when (result) {
                            WorkResult.Success, is WorkResult.Failure -> NSBackgroundActivityResultFinished
                            WorkResult.Retry -> NSBackgroundActivityResultDeferred
                        }
                    }
                completion?.invoke(activityResult)
            }
        }
    }

    /**
     * One-shot Retry path. Invalidates the current activity, schedules a fresh
     * `NSBackgroundActivityScheduler` with `interval = backoff.delayFor(attempt)`,
     * and returns the appropriate [NSBackgroundActivityResultFinished] for the
     * current attempt. Bounded by [request.backoff.maxAttempts] — once the cap
     * is exhausted the Retry converts to Failure and no resubmit happens.
     *
     * @return the activity result to pass to the system's completion handler
     *   for the *current* attempt. The fresh activity (if any) is launched
     *   as a side effect.
     */
    private fun handleOneShotRetry(
        request: WorkRequest.OneTime,
        attempt: Int,
    ): platform.Foundation.NSBackgroundActivityResult {
        val backoff = request.backoff
        val nextAttempt = attempt + 1
        if (nextAttempt >= backoff.maxAttempts) {
            log.w {
                "[${request.taskId}] one-shot reached maxAttempts(${backoff.maxAttempts}); " +
                    "converting Retry to Failure and dropping schedule"
            }
            // Treat as terminal: clear local tracking so scheduled() reflects reality.
            synchronized(lock) {
                activities.remove(request.taskId)?.invalidate()
                attempts.remove(request.taskId)
                kinds.remove(request.taskId)
                ephemeral.remove(request.taskId)
            }
            return NSBackgroundActivityResultFinished
        }

        // delayFor(attempt) — the attempt that *just failed* — matches Android's
        // WorkManager backoff curve and the iOS resubmit math.
        val delaySeconds = (backoff.delayFor(attempt).inWholeMilliseconds / 1000.0).coerceAtLeast(1.0)
        log.i { "[${request.taskId}] one-shot Retry; resubmitting in ${delaySeconds}s (attempt=$nextAttempt)" }

        synchronized(lock) {
            // Invalidate the current activity *before* scheduling the fresh one so the
            // map slot is cleanly reassigned. NSBackgroundActivityScheduler.invalidate
            // is documented as safe from any thread.
            activities.remove(request.taskId)?.invalidate()
            val freshActivity =
                NSBackgroundActivityScheduler(request.taskId.value).apply {
                    qualityOfService = NSQualityOfServiceUtility
                    repeats = false
                    interval = delaySeconds
                    tolerance = (interval * 0.1).coerceAtMost(interval)
                }
            activities[request.taskId] = freshActivity
            // Launch the fresh activity. The closure captures the same `request`, so
            // when it fires the worker sees the same input / constraints / backoff.
            launchActivity(freshActivity, request)
        }

        return NSBackgroundActivityResultFinished
    }

    override fun cancel(taskId: TaskId): CancelOutcome =
        synchronized(lock) {
            val activity = activities.remove(taskId) ?: return@synchronized CancelOutcome.NoSuchTask
            activity.invalidate()
            attempts.remove(taskId)
            kinds.remove(taskId)
            ephemeral.remove(taskId)
            eventListener.onCancelled(taskId)
            CancelOutcome.Cancelled(pendingCleared = 1)
        }

    override fun cancelAll(): CancelOutcome =
        synchronized(lock) {
            if (activities.isEmpty()) return@synchronized CancelOutcome.NoSuchTask
            val ids = activities.keys.toList()
            activities.values.forEach { it.invalidate() }
            activities.clear()
            attempts.clear()
            kinds.clear()
            ephemeral.clear()
            ids.forEach(eventListener::onCancelled)
            CancelOutcome.Cancelled(pendingCleared = ids.size)
        }

    override suspend fun scheduled(): List<ScheduledTask> =
        synchronized(lock) {
            activities.keys.map { id ->
                val attempt = attempts[id] ?: 0
                ScheduledTask(
                    taskId = id,
                    kind = kinds[id] ?: ScheduledTask.Kind.OneTime,
                    state = if (attempt > 0) ScheduledTask.State.Backoff else ScheduledTask.State.Pending,
                    nextRunHint = null,
                    attempt = attempt,
                    ephemeral = false, // Ephemeral state is tracked in EphemeralRegistry; surface there if needed.
                )
            }
        }

    override fun guarantees(): SchedulerGuarantees = MACOS_GUARANTEES

    /** Cancel everything and stop the scope. Called by Backgrounder on app termination if wired. */
    @Suppress("unused")
    fun shutdown() {
        cancelAll()
        scope.cancel()
    }
}

private val MACOS_GUARANTEES =
    SchedulerGuarantees(
        survivesProcessDeath = true,
        survivesReboot = true,
        survivesForceQuit = true,
        honoursWallClock = true, // approximately — CTS may delay.
        supportsRetryBackoff = true,
        cancelsInFlight = true,
        minimumPeriodicInterval = 1.seconds,
        maxConcurrentTasks = null,
    )

@Suppress("unused")
private fun Instant.epochMs(): Long = toEpochMilliseconds()

@Suppress("unused")
private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
