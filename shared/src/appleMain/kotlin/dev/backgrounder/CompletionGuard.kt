package dev.backgrounder

import kotlinx.atomicfu.atomic

/**
 * Single-fire latch that runs the supplied lambda **at most once**.
 *
 * Used by the iOS coroutine bridge to guard `BGTask.setTaskCompletedWithSuccess(_:)`.
 * Apple raises a fatal assertion if you call that method twice on the same `BGTask`,
 * and there are several races where it can happen — the worker calling it from
 * `applyResult`, the expiration handler firing during a suspending state-store write,
 * and `invokeOnCompletion` reporting cancellation. Wrapping every call site in
 * [runOnce] makes the second-and-later attempts no-ops.
 *
 * Atomic check-and-set, allocation-free on the hot path; matches the
 * `kotlinx-atomicfu`-only concurrency rule (CLAUDE.md §3 — no `kotlin.synchronized`,
 * no `@Synchronized`, no `volatile`).
 *
 * Not thread-confined; safe to call [runOnce] from any thread or coroutine.
 */
internal class CompletionGuard {
    private val fired = atomic(false)

    /**
     * Invokes [block] only on the first call. Subsequent calls return without
     * running anything. Returns `true` if [block] ran, `false` otherwise.
     *
     * Exceptions thrown by [block] propagate to the caller; the guard is still
     * marked as fired (so a partial-completion failure doesn't trigger a retry
     * that would re-attempt and double-complete a partially-completed task).
     */
    fun runOnce(block: () -> Unit): Boolean {
        if (!fired.compareAndSet(expect = false, update = true)) return false
        block()
        return true
    }

    /** True if [runOnce] has been called at least once. */
    val hasFired: Boolean get() = fired.value
}
