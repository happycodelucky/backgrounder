package com.happycodelucky.backgrounder

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The new constructed-instance entry point — held by the user's app graph for
 * the app's lifetime. This will be renamed to `Backgrounder` when the legacy
 * `object Backgrounder` is removed in the cut-over commit (plan §"DI-free
 * initialization" §9, step 5).
 *
 * Three things hang off the instance:
 *  - [scheduler]: the `Scheduler` surface (`schedule`, `cancel`, …). Hold a
 *    reference on the user's own app graph; never re-resolve.
 *  - [register]: associate a `TaskId` with a factory closure that builds a
 *    fresh `BackgroundWorker` per dispatch.
 *  - [start]: finalize init (seals the registry; iOS/macOS run the ephemeral
 *    sweep + register OS handlers + resurrect periodic schedules; Android
 *    flips the not-ready backstop). Idempotent.
 *  - [shutdown]: tear down library-owned coroutine scopes (iOS / macOS).
 *    Android is a no-op. Safe to call repeatedly.
 *
 * Construct via the per-platform extension factory:
 *   - `androidMain`: [Backgrounder.Companion.create] taking an `Application`.
 *   - `iosMain`: [Backgrounder.Companion.create] (no required args).
 *   - `macosMain`:   [Backgrounder.Companion.create] (no required args).
 *
 * `@OptIn(ExperimentalObjCName::class)`: standard SKIE annotation; stable in
 * practice and required for boundary refinement (CLAUDE.md §8).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "Backgrounder")
public class Backgrounder internal constructor(
    private val core: BackgrounderCore,
) {
    /**
     * The scheduling surface. Hold this on your app graph; passing it down is
     * the recommended way to expose scheduling capability to the rest of the
     * app without re-resolving from a singleton.
     */
    @ObjCName(swiftName = "scheduler")
    public val scheduler: Scheduler get() = core.scheduler

    /**
     * Register a [BackgroundWorker] factory for [taskId]. Must be called
     * before [start]. Throws if [start] has already run or [taskId] is
     * already registered.
     *
     * The factory closes over the user's DI graph (Koin, Hilt, hand-wired,
     * any of them — the library doesn't care). Each dispatch invokes the
     * factory afresh; workers are never cached.
     *
     * @throws IllegalStateException if [start] has already run.
     * @throws IllegalArgumentException if [taskId] is already registered.
     */
    @ObjCName(swiftName = "register")
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    public fun register(
        taskId: TaskId,
        factory: () -> BackgroundWorker,
    ) {
        core.registry.register(taskId, factory)
    }

    /**
     * Register a [BackgroundWorkerFactory] that owns many [TaskId]s at once.
     * Must be called before [start]. Throws if [start] has already run, or
     * any of the factory's [BackgroundWorkerFactory.taskIds] collide with an
     * existing per-id registration or another factory.
     *
     * This is the bulk alternative to per-id [register] — one factory object,
     * many ids, lazy worker resolution. See [BackgroundWorkerFactory] for the
     * `taskIds` / `create` sync contract.
     *
     * @throws IllegalStateException if [start] has already run.
     * @throws IllegalArgumentException if any of the factory's ids is already registered.
     */
    @ObjCName(swiftName = "register")
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    public fun register(factory: BackgroundWorkerFactory) {
        core.registry.register(factory)
    }

    /**
     * Finalize initialization. After this call:
     *   - the registry is sealed; further [register] calls throw;
     *   - iOS / macOS perform the ephemeral sweep and register OS handlers;
     *   - Android clears the not-ready backstop so workers that fire from
     *     this point onwards may dispatch.
     *
     * Idempotent — repeated calls are no-ops. Call exactly once at app launch.
     */
    @ObjCName(swiftName = "start")
    public fun start() {
        core.start()
    }

    /**
     * Run [task] immediately under [taskId] and suspend until it completes,
     * returning the typed result `R`.
     *
     * **Semantics — "raw" background dispatch.** Unlike scheduled work
     * ([scheduler] + [register]), `runNow`:
     *  - runs *immediately*, with no [WorkConstraints], no [BackoffPolicy], no
     *    retries, no [ExecutionHint] gating;
     *  - does **not** consult [WorkerRegistry] — the [task] lambda *is* the work,
     *    and [taskId] does **not** need to be `register()`-ed first;
     *  - is routed through the OS scheduling primitive on Android (`WorkManager`)
     *    and iOS (`BGTaskScheduler`) so the work earns background runtime if
     *    the app is suspended mid-call; on macOS it runs on a library-owned
     *    `SupervisorJob` scope (`NSBackgroundActivityScheduler` is interval-shaped
     *    and a poor fit for one-shot work).
     *
     * **Pre-emption — "last call wins".** If a `runNow` is already in flight
     * for [taskId], or a scheduled run for [taskId] is pending or executing,
     * `runNow` cancels them all (via [cancel]) *before* submitting its own
     * request. The prior caller's `await` rethrows `CancellationException`.
     * This is necessary because `runNow` returns a typed result to a specific
     * caller — two concurrent runs for the same id would be ambiguous.
     *
     * **Cancellation.** Caller cancellation flows through structured concurrency:
     * the OS request is cancelled (best-effort on iOS — `BGTaskScheduler`
     * cannot kill a *running* handler, only pending requests; an in-flight
     * lambda is cancelled via the in-process bridge `Job`), the `task` lambda
     * observes `CancellationException`, and `runNow` rethrows.
     *
     * **Exceptions.** A `Throwable` thrown by [task] propagates to the caller's
     * `await`. The platform layer reports `WorkResult.Failure` to the OS so
     * it doesn't treat the process as crashed. SKIE bridges this as Swift
     * `async throws -> R`.
     *
     * **iOS `Info.plist` requirement.** [taskId]`.value` *must* appear in the
     * app's `BGTaskSchedulerPermittedIdentifiers` array. If it does not,
     * `BGTaskScheduler.submit` rejects the request and `runNow` throws an
     * `IllegalStateException` whose message names the missing identifier.
     *
     * @throws IllegalStateException if [start] has not been called yet, or
     *   the platform refuses the request (iOS only — see above).
     */
    @ObjCName(swiftName = "run")
    @Throws(IllegalStateException::class)
    public suspend fun <R> runNow(
        taskId: TaskId,
        task: suspend () -> R,
    ): R {
        check(core.isStarted) {
            "Backgrounder.runNow($taskId): start() has not been called yet."
        }
        // Pre-empt anything else for this id (in-flight runNow, pending schedule,
        // in-flight scheduled worker). The prior runNow caller — if any — sees
        // CancellationException from their await.
        cancel(taskId)
        return core.instantRunner.run(taskId, task)
    }

    /**
     * Cancel everything the library knows about for [taskId]:
     *  - any pending scheduled request (delegates to [Scheduler.cancel]);
     *  - any in-flight scheduled worker (best-effort per platform);
     *  - any in-flight [runNow] call (its `Deferred` completes with
     *    `CancellationException`, the caller's `await` rethrows).
     *
     * Returns `Cancelled(pendingCleared)` where `pendingCleared` is the
     * platform-reported count from [Scheduler.cancel] (best-effort — Android
     * reports an accurate count; iOS reports 0 or 1). `runNow` cancellations
     * are **not** added to `pendingCleared` — that field's existing meaning
     * is "platform-pending requests removed" and we keep it stable.
     *
     * Returns `NoSuchTask` only when neither a scheduled request nor an
     * in-flight `runNow` existed for [taskId].
     *
     * For "scheduled-only" cancellation, use [scheduler]`.cancel(taskId)` —
     * it remains unchanged.
     */
    @ObjCName(swiftName = "cancel")
    public fun cancel(taskId: TaskId): CancelOutcome {
        val schedulerOutcome = core.scheduler.cancel(taskId)
        val cancelledRunNow = core.instantRunner.cancelInFlight(taskId)
        return when (schedulerOutcome) {
            is CancelOutcome.Cancelled -> {
                schedulerOutcome
            }

            CancelOutcome.NoSuchTask -> {
                if (cancelledRunNow) CancelOutcome.Cancelled(pendingCleared = 0) else CancelOutcome.NoSuchTask
            }
        }
    }

    /**
     * Tear down library-owned coroutine scopes (CLAUDE.md §3 — every scope has
     * a clear owner with a defined cancellation lifecycle).
     *
     * **iOS / macOS:** cancels the scope owned by the platform scheduler /
     * coroutine bridge. In-flight workers observe a `CancellationException`;
     * the per-task completion guard reports the iOS-level task as
     * `setTaskCompletedWithSuccess(false)` exactly once.
     *
     * **Android:** no-op. WorkManager owns its own dispatcher.
     *
     * Safe to call multiple times. Typically called from app teardown — e.g.
     * an iOS test's `tearDown`, or from `applicationWillTerminate` on macOS.
     */
    @ObjCName(swiftName = "shutdown")
    public fun shutdown() {
        core.shutdown()
    }

    /**
     * Companion object exists so per-platform source sets can install a
     * `Backgrounder.Companion.create(...)` extension factory. (`commonMain`
     * cannot define `create` because the Android variant requires an
     * `Application` and the Apple variants don't — there's no common
     * signature that doesn't leak `Any?`.)
     */
    public companion object
}
