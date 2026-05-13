package com.happycodelucky.backgrounder

import kotlinx.atomicfu.atomic

/**
 * Internal value object held by [Backgrounder] that bundles the per-platform
 * graph (registry + scheduler) plus the platform-specific `start` / `shutdown`
 * lambdas the public `Backgrounder.start()` and `.shutdown()` calls dispatch to.
 *
 * Constructed by per-platform builders (`AndroidBackgrounderBuilder`,
 * `IOSBackgrounderBuilder`, `MacOSBackgrounderBuilder`) inside `androidMain` /
 * `iosMain` / `macosMain`. Plan §"DI-free initialization" §1.1.
 *
 * The forward-reference puzzle that DI used to solve (a closure capturing an
 * object that hasn't been built yet) is dissolved here by *constructor order* —
 * the platform builder constructs the scheduler first, then captures it inside
 * the `onStart` / `onShutdown` lambdas it passes here. No `lateinit`, no `Lazy`.
 */
internal class BackgrounderCore(
    val registry: WorkerRegistry,
    val scheduler: Scheduler,
    val instantRunner: InstantRunner,
    private val onStart: () -> Unit,
    private val onShutdown: () -> Unit,
) {
    private val started = atomic(false)

    /**
     * Whether [start] has been called. Read by [Backgrounder.runNow] to gate
     * dispatch — instant runs require the registry to be sealed before they
     * can rely on stable platform handlers being installed.
     */
    val isStarted: Boolean get() = started.value

    /** Idempotent — repeated calls return without invoking [onStart] again. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        registry.seal()
        onStart()
    }

    /** Idempotent — repeated calls invoke [onShutdown] each time, but the
     *  per-platform shutdown logic is itself idempotent (cancels a scope, etc).
     *  We don't gate this with the started flag because shutdown should always
     *  succeed even if start() was never called. */
    fun shutdown() {
        onShutdown()
    }
}
