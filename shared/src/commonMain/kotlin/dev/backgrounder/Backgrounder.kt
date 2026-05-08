package dev.backgrounder

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
