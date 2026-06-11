package com.happycodelucky.backgrounder

import kotlinx.serialization.Serializable

/**
 * Conditions a [WorkRequest] needs satisfied before the platform scheduler will
 * dispatch it.
 *
 * v1 exposes the lowest-common-denominator across Android and iOS. Android-only
 * constraints (storage-not-low, battery-not-low, device-idle, content URI
 * triggers) land in v2 via a `WorkConstraints.Android` extension.
 */
@Serializable
public data class WorkConstraints(
    /** Network connectivity level required before the scheduler dispatches this request. */
    val networkRequired: NetworkRequirement = NetworkRequirement.None,
    /** If `true`, the device must be connected to external power before dispatching. */
    val requiresCharging: Boolean = false,
)

/**
 * Network connectivity requirement for a [WorkRequest].
 *
 * [Unmetered] is honoured on all platforms by the library's pre-execution
 * reachability gate (`ReachabilityStatus.isDataMetered == false`). On iOS, the
 * OS-level scheduling hint (`BGProcessingTaskRequest.requiresNetworkConnectivity`)
 * is downgraded to `Any` (logged once) because `BGTaskScheduler` has no
 * metered/unmetered concept — but the gate still blocks dispatch until an
 * unmetered connection is available.
 */
public enum class NetworkRequirement {
    /** No network requirement — work runs regardless of connectivity. */
    None,

    /** Any active network. Maps to Android `CONNECTED` / iOS `requiresNetworkConnectivity = true`. */
    Any,

    /** Unmetered network (Wi-Fi / Ethernet). Honoured by the reachability gate on all platforms.
     * Android additionally honours this at the OS dispatch level via `NetworkType.UNMETERED`. */
    Unmetered,
}
