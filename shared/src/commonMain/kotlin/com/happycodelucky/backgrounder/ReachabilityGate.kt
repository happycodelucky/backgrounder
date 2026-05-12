package com.happycodelucky.backgrounder

import com.happycodelucky.reachable.Metering
import com.happycodelucky.reachable.Reachability
import com.happycodelucky.reachable.ReachabilityStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pre-execution network gate that holds a worker dispatch for a short window
 * until [Reachability] reports the requirement is satisfied.
 *
 * Closes the iOS / macOS gap where Apple's background-task primitives either
 * advisory-honour or entirely ignore `WorkConstraints.networkRequired`. The
 * gate is invisible to user worker code — schedulers / dispatchers call
 * [awaitReachable] between the OS firing the worker and `worker.execute(...)`
 * being invoked. On timeout the caller short-circuits to [WorkResult.Retry]
 * so the scheduler reschedules per the request's `BackoffPolicy`.
 *
 * Android does **not** use this class. Jetpack `WorkManager` natively refuses
 * to dispatch a worker whose `Constraints.networkType` is unmet; the OS holds
 * the worker for us. Carrying the gate on Android would be both wasted work
 * and a behaviour drift versus the WorkManager contract.
 *
 * The wait window is bounded by the per-invocation platform budget. The
 * formula `min(5.seconds, budget / 4)` keeps the gate from burning more than
 * a quarter of an `BGAppRefreshTask`'s ~30-second runway and never waits more
 * than 5 seconds even on long-budget `BGProcessingTask` (several minutes) or
 * the in-process foreground feed (`Duration.INFINITE`).
 *
 * Concurrency: [awaitReachable] is `suspend`-safe and respects upstream
 * coroutine cancellation. `withTimeoutOrNull` propagates the test scheduler's
 * virtual time under `runTest`, so unit tests need no clock injection.
 */
internal class ReachabilityGate(
    private val reachability: Reachability,
) {
    /**
     * Outcome of a single gate call. Sealed (CLAUDE.md §3) so callers can
     * distinguish "no wait needed" from "we waited and timed out" for
     * structured logging / metrics without re-deriving from a `Boolean`.
     */
    internal sealed interface GateResult {
        /** The request didn't require a network — gate short-circuited without observing reachability. */
        public data object NotRequired : GateResult

        /** Network is reachable (and matches the metering requirement, where applicable). */
        public data object Met : GateResult

        /** Budget exhausted without the requirement being met. Caller should map to [WorkResult.Retry]. */
        public data object TimedOut : GateResult
    }

    /**
     * Suspend until [requirement] is satisfied, or until the wait window
     * derived from [budget] elapses.
     *
     * `NetworkRequirement.None` returns [GateResult.NotRequired] without
     * touching the reachability flow — zero allocation on the hot path for
     * the common no-constraint case.
     *
     * `NetworkRequirement.Unmetered` is honoured against [Metering.Unmetered]
     * — wifi or ethernet only. An iPhone on cellular hotspot has
     * `reachable = true, metering = Metered`; the `Unmetered` gate correctly
     * keeps waiting in that case. This is a fidelity improvement over the
     * legacy behaviour where iOS downgraded `Unmetered` to `Any`.
     */
    suspend fun awaitReachable(
        requirement: NetworkRequirement,
        budget: Duration,
    ): GateResult {
        if (requirement == NetworkRequirement.None) return GateResult.NotRequired
        if (matches(requirement, reachability.status.value)) return GateResult.Met
        val wait = minOf(MAX_WAIT, budget / 4)
        val result =
            withTimeoutOrNull(wait) {
                reachability.status.first { matches(requirement, it) }
            }
        return if (result != null) GateResult.Met else GateResult.TimedOut
    }

    private fun matches(
        requirement: NetworkRequirement,
        status: ReachabilityStatus,
    ): Boolean {
        if (!status.reachable) return false
        return when (requirement) {
            NetworkRequirement.None -> true
            NetworkRequirement.Any -> true
            NetworkRequirement.Unmetered -> status.metering == Metering.Unmetered
        }
    }

    internal companion object {
        /** Hard cap on the gate wait — never holds a worker longer than this regardless of budget. */
        internal val MAX_WAIT: Duration = 5.seconds
    }
}

/**
 * Computes the [ReachabilityGate] wait window for the given platform [PlatformCapabilities].
 *
 * `min(MAX_WAIT, maxExecutionTime / 4)` — quarters the budget so the gate
 * doesn't burn most of an App Refresh runway, then clamps at 5s for the
 * common case where `maxExecutionTime` is `Duration.INFINITE` (foreground
 * feed) or several minutes (BGProcessingTask). Foreground feed inherits the
 * 5-second cap implicitly via this formula.
 */
internal fun gateBudgetFor(capabilities: PlatformCapabilities): Duration =
    minOf(ReachabilityGate.MAX_WAIT, capabilities.maxExecutionTime / 4)
