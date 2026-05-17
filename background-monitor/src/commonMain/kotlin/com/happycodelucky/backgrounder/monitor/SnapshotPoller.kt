package com.happycodelucky.backgrounder.monitor

import com.happycodelucky.backgrounder.Backgrounder
import com.happycodelucky.backgrounder.PlatformDiagnostics
import com.happycodelucky.backgrounder.ScheduledTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * Polls the inspector APIs ([Backgrounder.scheduled] /
 * [Backgrounder.diagnostics]) on an interval and surfaces the latest
 * snapshot through a [StateFlow] pair.
 *
 * Inspector UIs typically want both the *event stream* (via [Monitor]) and
 * the *current state* (the inspector APIs) on a refresh cadence. This
 * helper takes care of the polling loop and back-pressure:
 *  - One `StateFlow<List<ScheduledTask>>` reflects the latest `scheduled()`
 *    response.
 *  - One `StateFlow<PlatformDiagnostics>` reflects the latest
 *    `diagnostics()` response.
 *  - The poll coroutine sleeps for [interval] between polls; intervals
 *    shorter than ~250 ms are discouraged (the underlying iOS query is
 *    callback-shaped and the Android query reads `WorkInfo` over an IPC).
 *
 * **Scope ownership.** The poller's loop runs on the [CoroutineScope]
 * passed to [start]. Cancel the scope to stop polling. The flows continue
 * to hold their last-emitted value indefinitely; consumers reading after
 * cancellation see a frozen view.
 *
 * **Initial state.** Before the first successful poll completes, both
 * flows emit `null` / [PlatformDiagnostics.Healthy] respectively. After
 * that, every poll either updates the flow value or leaves it unchanged
 * if nothing changed (StateFlow's distinct-equal de-duplication).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "SnapshotPoller")
public class SnapshotPoller(
    private val backgrounder: Backgrounder,
    private val interval: Duration,
) {
    private val _scheduled = MutableStateFlow<List<ScheduledTask>?>(null)

    /**
     * Most recent `scheduled()` snapshot, or `null` until the first poll
     * completes. SKIE bridges this as `AsyncSequence<[ScheduledTask]?>`.
     */
    @ObjCName(swiftName = "scheduled")
    public val scheduled: StateFlow<List<ScheduledTask>?> get() = _scheduled.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlatformDiagnostics.Healthy)

    /**
     * Most recent `diagnostics()` snapshot. Defaults to
     * [PlatformDiagnostics.Healthy] until the first poll.
     */
    @ObjCName(swiftName = "diagnostics")
    public val diagnostics: StateFlow<PlatformDiagnostics> get() = _diagnostics.asStateFlow()

    private var loop: Job? = null

    /**
     * Begin polling on [scope]. Idempotent — calling twice is treated as a
     * restart on the new scope; the previous loop is cancelled. Returns
     * the [Job] driving the loop so callers can join or cancel it directly
     * if they prefer.
     */
    @ObjCName(swiftName = "start")
    public fun start(scope: CoroutineScope): Job {
        loop?.cancel()
        val job =
            scope.launch {
                while (isActive) {
                    pollNow() // one-shot poll; updates _scheduled and _diagnostics.
                    delay(interval)
                }
            }
        loop = job
        return job
    }

    /** Stop polling. Idempotent. */
    @ObjCName(swiftName = "stop")
    public fun stop() {
        loop?.cancel()
        loop = null
    }

    /**
     * Run a single poll immediately, updating [scheduled] and [diagnostics]
     * synchronously with the call. Independent of the interval loop —
     * works whether [start] has been called or not, and does not reset the
     * interval cadence either way.
     *
     * Useful for "refresh on demand" UX (e.g. a user-pulled refresh
     * gesture, or a `WorkCompleted` event triggering an immediate
     * snapshot update). The interval loop's next poll still fires on its
     * regular cadence; this call is purely additive.
     *
     * Errors are swallowed for parity with the interval loop — a failed
     * poll leaves the flows holding their previous values. If you need to
     * surface the failure, call [com.happycodelucky.backgrounder.Backgrounder.scheduled]
     * and [com.happycodelucky.backgrounder.Backgrounder.diagnostics]
     * directly instead.
     *
     * Concurrent calls (and concurrency with the interval loop) are safe —
     * `MutableStateFlow.value` is thread-safe and the two writes
     * (`_scheduled`, `_diagnostics`) are independent of each other. The
     * last writer wins per flow.
     */
    @ObjCName(swiftName = "pollNow")
    public suspend fun pollNow() {
        runCatching {
            _scheduled.value = backgrounder.scheduled()
            _diagnostics.value = backgrounder.diagnostics()
        } // swallow — parity with the interval loop.
    }
}
