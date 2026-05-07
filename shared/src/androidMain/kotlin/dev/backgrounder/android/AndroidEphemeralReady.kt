package dev.backgrounder.android

import kotlinx.atomicfu.atomic

/**
 * Process-local flag that backs `Backgrounder.markReady()`.
 *
 * Read by [RegistryDispatchWorker]; if `false` and the request was scheduled
 * with `ephemeral = true`, the worker bails before invoking user code. This
 * is the backstop for the rare case where `JobScheduler` fires an ephemeral
 * worker between `Backgrounder.attachTo` and the user's full init —
 * documented in the plan's "Important Android gotcha" paragraph.
 *
 * The Android sweep in `attachTo` deliberately reset this to `false` on
 * every cold start; `markReady()` flips it `true`.
 */
internal object AndroidEphemeralReady {
    private val flag = atomic(false)

    fun markReady() {
        flag.value = true
    }

    fun reset() {
        flag.value = false
    }

    fun snapshot(): Boolean = flag.value
}
