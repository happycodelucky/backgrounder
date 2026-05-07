package dev.backgrounder.android

import android.content.Context
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.TaskId
import java.util.concurrent.TimeUnit

/**
 * Cancels every pending ephemeral request before any worker can dispatch.
 *
 * Called from `Backgrounder.attachTo(application)` — which runs as the
 * **first line** of `Application.onCreate`, *before* `startKoin` and *before*
 * the user's app graph is available.
 *
 * Because we run pre-Koin and the only available execution context is the
 * main thread (no coroutine scope yet), we use a small bounded `await` on the
 * `Operation`s WorkManager hands back. A 5-second deadline means the cold
 * start path is capped — any longer and we log loudly and continue.
 */
internal class AndroidEphemeralSweep(
    private val context: Context,
    private val ephemeral: EphemeralRegistry,
) {
    private val log = Logger.withTag("Backgrounder/EphemeralSweep")

    fun run() {
        val ids: Set<TaskId> = ephemeral.snapshot()
        if (ids.isEmpty()) {
            log.d { "no ephemeral entries to sweep" }
            return
        }
        log.i { "sweeping ${ids.size} ephemeral request(s) before app init" }
        val workManager = WorkManager.getInstance(context)

        // Fire all cancellations up-front (they're enqueued in parallel by
        // WorkManager), then wait against a single shared budget. This caps
        // the cold-start cost at SWEEP_DEADLINE_MS no matter how many ids are
        // pending — vs. SWEEP_DEADLINE_MS × N if we awaited each one.
        val operations = ids.map { id -> id to workManager.cancelUniqueWork(id.value) }
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SWEEP_DEADLINE_MS)
        operations.forEach { (id, op) ->
            val remainingMs = TimeUnit.NANOSECONDS.toMillis((deadlineNs - System.nanoTime()).coerceAtLeast(0L))
            if (remainingMs == 0L) {
                log.w { "sweep budget exhausted before cancelling $id; continuing without await" }
                return@forEach
            }
            try {
                op.result.get(remainingMs, TimeUnit.MILLISECONDS)
                log.d { "cancelled ephemeral $id" }
            } catch (t: Throwable) {
                log.e(t) { "failed to cancel ephemeral $id within remaining ${remainingMs}ms" }
            }
        }
        ephemeral.clear()
    }

    internal companion object {
        /** Total wall budget for the cold-start sweep across *all* ephemeral ids. */
        internal const val SWEEP_DEADLINE_MS: Long = 5_000L
    }
}
