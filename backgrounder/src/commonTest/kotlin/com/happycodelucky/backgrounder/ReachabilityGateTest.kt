package com.happycodelucky.backgrounder

import com.happycodelucky.reachable.ReachabilityStatus
import com.happycodelucky.reachable.Transport
import com.happycodelucky.reachable.testing.FakeReachability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async as coroutinesAsync

// --- Small file-local helpers around the upstream FakeReachability API.
//
// `:reachable-testing` exposes a constructor `FakeReachability(initial: ReachabilityStatus)`
// plus per-axis setters. These two helpers keep the gate test's call sites
// matching the shape we used pre-0.11.10 (`offline()` / `online(transport, isDataMetered)`)
// without re-introducing the hand-rolled fake. Drop these if more sites
// need them; promote into a shared test helper.

private fun fakeOffline(): FakeReachability = FakeReachability()

private fun fakeOnline(
    transport: Transport = Transport.Wifi,
    isDataMetered: Boolean = false,
): FakeReachability =
    FakeReachability(
        ReachabilityStatus(isReachable = true, transport = transport, isDataMetered = isDataMetered),
    )

/**
 * Pure-logic tests for [ReachabilityGate].
 *
 * The gate is the load-bearing piece of the iOS / macOS network-constraint
 * story — its `awaitReachable` is called between OS-driven worker dispatch
 * and `worker.execute(...)`. Tests use [FakeReachability] (in commonTest)
 * to drive reachability transitions deterministically under virtual time.
 *
 * The gate budget formula (`min(MAX_WAIT, budget / 4)`) is exercised via
 * the [gateBudgetFor] helper plus direct `awaitReachable` calls with
 * deliberately-chosen budgets.
 */
class ReachabilityGateTest {
    private val generousBudget = 1.minutes // budget/4 = 15s, then clamped to 5s
    private val capabilitiesGenerous = PlatformCapabilities(generousBudget, cancelsInFlight = false)

    @Test
    fun networkRequirementNoneShortCircuits() =
        runTest {
            // Even with an offline fake, NetworkRequirement.None must short-circuit
            // without observing reachability. This is the zero-allocation hot path
            // for workers that don't need a network at all.
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val result = gate.awaitReachable(NetworkRequirement.None, generousBudget)
            assertIs<ReachabilityGate.GateResult.NotRequired>(result)
        }

    @Test
    fun alreadyReachableReturnsMetWithoutWaiting() =
        runTest {
            val reachability = fakeOnline()
            val gate = ReachabilityGate(reachability)
            val before = currentTime
            val result = gate.awaitReachable(NetworkRequirement.Any, generousBudget)
            assertIs<ReachabilityGate.GateResult.Met>(result)
            assertEquals(before, currentTime, "must not advance virtual time when status is already satisfied")
        }

    @Test
    fun becomesReachableMidWaitReturnsMet() =
        runTest {
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val deferredResult =
                asyncUnconfined { gate.awaitReachable(NetworkRequirement.Any, generousBudget) }

            // Let the gate suspend on the flow, then flip the fake online.
            // Virtual time advance is needed so withTimeoutOrNull's clock progresses
            // and the flow's collector is registered before we emit.
            advanceTimeBy(100.milliseconds)
            reachability.emit(
                ReachabilityStatus(isReachable = true, transport = Transport.Wifi, isDataMetered = false),
            )
            val result = deferredResult.await()
            assertIs<ReachabilityGate.GateResult.Met>(result)
            // Strictly less than the 5-second cap — we emitted long before timeout.
            assertTrue(
                currentTime < ReachabilityGate.MAX_WAIT.inWholeMilliseconds,
                "should resolve before MAX_WAIT (currentTime=$currentTime ms)",
            )
        }

    @Test
    fun staysOfflineThroughBudgetReturnsTimedOut() =
        runTest {
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val result = gate.awaitReachable(NetworkRequirement.Any, generousBudget)
            assertIs<ReachabilityGate.GateResult.TimedOut>(result)
            // Should have consumed the MAX_WAIT cap (5s with 1-minute budget,
            // since min(5s, 60s/4) == min(5s, 15s) == 5s).
            assertEquals(
                ReachabilityGate.MAX_WAIT.inWholeMilliseconds,
                currentTime,
                "timeout should consume exactly MAX_WAIT under a generous budget",
            )
        }

    @Test
    fun unmeteredRequirementWaitsThroughMeteredEmission() =
        runTest {
            // Start offline; transition to Metered cellular; then to Unmetered wifi.
            // Gate must only resolve at the Unmetered transition — Metered should
            // *not* satisfy NetworkRequirement.Unmetered.
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val deferred =
                asyncUnconfined { gate.awaitReachable(NetworkRequirement.Unmetered, generousBudget) }

            advanceTimeBy(50.milliseconds)
            // Cellular — reachable but Metered. Gate must keep waiting.
            reachability.emit(
                ReachabilityStatus(isReachable = true, transport = Transport.Cellular, isDataMetered = true),
            )
            advanceTimeBy(50.milliseconds)
            // Should still be waiting; deferred has not completed.
            assertTrue(
                deferred.isActive,
                "Unmetered gate must not resolve while metering is Metered (cellular hotspot scenario)",
            )

            // Now flip to Wi-Fi (Unmetered).
            reachability.emit(
                ReachabilityStatus(isReachable = true, transport = Transport.Wifi, isDataMetered = false),
            )
            val result = deferred.await()
            assertIs<ReachabilityGate.GateResult.Met>(result)
        }

    @Test
    fun shortBudgetClampsBelowMaxWait() =
        runTest {
            // budget = 8s → budget/4 = 2s, which is < MAX_WAIT (5s). The gate
            // should time out at the smaller value, not the cap.
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val budget = 8.seconds
            val result = gate.awaitReachable(NetworkRequirement.Any, budget)
            assertIs<ReachabilityGate.GateResult.TimedOut>(result)
            assertEquals(
                2.seconds.inWholeMilliseconds,
                currentTime,
                "short budget should clamp gate wait to budget/4 (2s) rather than MAX_WAIT (5s)",
            )
        }

    @Test
    fun cancellationPropagates() =
        runTest {
            // The gate respects upstream coroutine cancellation — the caller's
            // scope owns lifecycle, not the gate. Tests cancellation by launching
            // the gate on a child scope and cancelling that scope.
            val reachability = fakeOffline()
            val gate = ReachabilityGate(reachability)
            val parentScope = CoroutineScope(coroutineContext + SupervisorJob())
            val job =
                parentScope.launch {
                    gate.awaitReachable(NetworkRequirement.Any, generousBudget)
                }
            advanceTimeBy(100.milliseconds)
            assertTrue(job.isActive, "gate should still be waiting")
            parentScope.cancel()
            // The cancelled job completes with a CancellationException — the test
            // passes as long as the scope tears down cleanly without throwing.
            job.join()
            assertTrue(job.isCancelled)
        }

    @Test
    fun gateBudgetForCapsAtMaxWait() {
        // Direct check of the helper. Infinite budget (foreground feed) clamps
        // to MAX_WAIT; 1-minute budget clamps to MAX_WAIT (since 15s > MAX_WAIT).
        val foreground = PlatformCapabilities(kotlin.time.Duration.INFINITE, cancelsInFlight = true)
        val standard = PlatformCapabilities(generousBudget, cancelsInFlight = false)
        assertEquals(ReachabilityGate.MAX_WAIT, gateBudgetFor(foreground))
        assertEquals(ReachabilityGate.MAX_WAIT, gateBudgetFor(standard))
    }

    @Test
    fun gateBudgetForFollowsQuarteringBelowCap() {
        // 8-second budget → 2-second gate wait (well below MAX_WAIT).
        val tight = PlatformCapabilities(8.seconds, cancelsInFlight = false)
        assertEquals(2.seconds, gateBudgetFor(tight))
    }

    // --- TestScope conveniences ---------------------------------------------

    private val TestScope.currentTime: Long get() = testScheduler.currentTime

    private fun TestScope.advanceTimeBy(duration: kotlin.time.Duration) {
        testScheduler.advanceTimeBy(duration.inWholeMilliseconds)
    }

    private suspend fun <T> TestScope.asyncUnconfined(block: suspend CoroutineScope.() -> T): kotlinx.coroutines.Deferred<T> =
        coroutinesAsync(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { coroutineScope { block() } }
}
