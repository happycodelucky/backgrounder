package com.happycodelucky.backgrounder

import com.happycodelucky.reachable.Metering
import com.happycodelucky.reachable.Reachability
import com.happycodelucky.reachable.ReachabilityStatus
import com.happycodelucky.reachable.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Test double for [com.happycodelucky.reachable.Reachability] used across the
 * `commonTest`, `appleTest`, and `iosTest` source sets.
 *
 * Backed by a [MutableStateFlow] so tests can call [emit] to drive reachability
 * transitions deterministically (e.g. "start offline, then become reachable
 * after virtual time advances by 200 ms"). `close()` is a no-op to match
 * `Reachability.shared`'s contract — every test fake should be safely
 * closeable by the library without affecting subsequent assertions.
 *
 * The two derived flows (`reachable` and `lowDataMode`) are produced via
 * `status.map { … }.stateIn(...)`. We accept the small dependency on
 * `GlobalScope` here because:
 *
 * 1. It's *only* in test code (no production reach).
 * 2. `SharingStarted.Eagerly` plus the fake's process-lifetime nature means
 *    the derived flows live exactly as long as the test process.
 * 3. The alternative — taking a `CoroutineScope` parameter — would force
 *    every call site to thread a scope through, breaking the simple
 *    `FakeReachability(initial)` ergonomics tests expect from a fake.
 *
 * In production code, library CoroutineScope ownership rules (CLAUDE.md §3)
 * still apply.
 */
@Suppress("OPT_IN_USAGE")
internal class FakeReachability(
    initial: ReachabilityStatus = ReachabilityStatus.Unknown,
    private val scope: CoroutineScope = GlobalScope,
) : Reachability {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<ReachabilityStatus> = _status.asStateFlow()

    override val isReachable: Boolean
        get() = _status.value.reachable

    override val isLowDataMode: Boolean
        get() = _status.value.metering == Metering.Constrained

    override val reachable: StateFlow<Boolean> =
        _status
            .map { it.reachable }
            .stateIn(scope, SharingStarted.Eagerly, initial.reachable)

    override val lowDataMode: StateFlow<Boolean> =
        _status
            .map { it.metering == Metering.Constrained }
            .stateIn(scope, SharingStarted.Eagerly, initial.metering == Metering.Constrained)

    /**
     * Drive the fake to a new reachability state. Equivalent to the platform
     * observer emitting; subscribers of [status] / [reachable] / [lowDataMode]
     * observe the new value on their next collection.
     */
    fun emit(status: ReachabilityStatus) {
        _status.value = status
    }

    /** Convenience: drive online with a sensible default transport / metering. */
    fun emitOnline(
        transport: Transport = Transport.Wifi,
        metering: Metering = Metering.Unmetered,
    ) {
        emit(ReachabilityStatus(reachable = true, transport = transport, metering = metering))
    }

    /** Convenience: drive offline. */
    fun emitOffline() {
        emit(ReachabilityStatus(reachable = false, transport = Transport.None, metering = Metering.Unmetered))
    }

    /** No-op — matches `Reachability.shared.close()`. Tests should never observe a closed state. */
    override fun close() = Unit

    internal companion object {
        /** Shorthand for an always-online fake (Wi-Fi, unmetered). */
        fun online(): FakeReachability =
            FakeReachability(
                ReachabilityStatus(reachable = true, transport = Transport.Wifi, metering = Metering.Unmetered),
            )

        /** Shorthand for an always-offline fake. */
        fun offline(): FakeReachability =
            FakeReachability(
                ReachabilityStatus(reachable = false, transport = Transport.None, metering = Metering.Unmetered),
            )
    }
}
