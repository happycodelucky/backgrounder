package dev.backgrounder

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Top-level lifecycle hooks. Platform actuals decide what each call does:
 *
 * **Android** (`androidMain`):
 * - [attachTo] is the *first* call from `Application.onCreate`, **before**
 *   `startKoin`. It performs the ephemeral cold-launch sweep, cancelling any
 *   pending ephemeral work using `WorkManager.cancelUniqueWork`. After it
 *   returns the user starts Koin and registers worker factories.
 * - [registerHandlers] on Android does nothing other than seal the registry —
 *   WorkManager's bridge is wired through Koin's `WorkerFactory`. Provided for
 *   API symmetry with iOS / macOS.
 * - [markReady] flips an `AtomicBoolean` checked by `RegistryDispatchWorker`
 *   to backstop the rare race of WorkManager firing an ephemeral worker
 *   between [attachTo] and full app init.
 *
 * **iOS / macOS** (`appleMain`):
 * - [attachTo] is a no-op (Android-only concept).
 * - [registerHandlers] is the *only* call from
 *   `application(_:didFinishLaunchingWithOptions:)` /
 *   `applicationDidFinishLaunching`. It runs the ephemeral sweep, registers
 *   `BGTaskScheduler` / `NSBackgroundActivityScheduler` handlers for every
 *   id in [WorkerRegistry.registeredIds], and resurrects any active periodic
 *   tasks. Must run before the launch method returns.
 * - [markReady] is a no-op (no JobScheduler-fires-during-init race on Apple).
 *
 * `@OptIn(ExperimentalObjCName::class)`: standard Swift-rename annotation;
 * stable in practice and required by SKIE for boundary refinement.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "BackgrounderRuntime")
public object Backgrounder {
    /**
     * Android-only entry point. Pass your `Application` (cast as [Any] in
     * `commonMain` — the Android actual narrows). On non-Android targets this
     * is a no-op, kept for API symmetry.
     */
    @ObjCName(swiftName = "attachTo")
    public fun attachTo(application: Any?) {
        platformAttachTo(application)
    }

    /**
     * Register OS-level handlers. Must be called once at app launch *after*
     * [WorkerRegistry.register]ing every factory and *before* the launch method
     * returns.
     */
    @ObjCName(swiftName = "registerHandlers")
    public fun registerHandlers() {
        platformRegisterHandlers()
    }

    /**
     * Signal that any further app init the workers depend on is complete.
     * Android: clears the ephemeral-not-ready backstop. iOS / macOS: no-op.
     */
    @ObjCName(swiftName = "markReady")
    public fun markReady() {
        platformMarkReady()
    }

    /**
     * Tear down library-owned coroutine scopes.
     *
     * **iOS / macOS:** cancels the [kotlinx.coroutines.CoroutineScope] owned by
     * the Apple-platform scheduler / coroutine bridge. CLAUDE.md §3 requires
     * every `CoroutineScope` to have a clear owner with a defined cancellation
     * lifecycle; this is that lifecycle hook. In-flight workers observe a
     * `CancellationException` and the per-task completion guard reports the
     * iOS-level task as `setTaskCompletedWithSuccess(false)` exactly once.
     *
     * **Android:** no-op. WorkManager owns its own dispatch scope; the library
     * does not hold one.
     *
     * Safe to call multiple times. Typically called from app teardown — e.g. an
     * iOS test's `tearDown`, or in production never, since the OS reclaims the
     * process. The macOS desktop app should call this from
     * `applicationWillTerminate`.
     */
    @ObjCName(swiftName = "shutdown")
    public fun shutdown() {
        platformShutdown()
    }
}

internal expect fun platformAttachTo(application: Any?)

internal expect fun platformRegisterHandlers()

internal expect fun platformMarkReady()

internal expect fun platformShutdown()
