package com.happycodelucky.backgrounder

import com.happycodelucky.backgrounder.ios.IOSBackgrounderBuilder
import com.happycodelucky.reachable.Reachability
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

/**
 * iOS factory for [Backgrounder].
 *
 * Hold the returned instance for the lifetime of the app — typically as a
 * stored property on `AppDelegate`. The Swift call site reads:
 *
 * ```swift
 * let backgrounder = Backgrounder.companion.create(
 *     tickIdentifier: "com.example.app.background-tick"
 * )
 * backgrounder.register(taskId: SyncWorker.companion.ID) { /* SyncWorker(…) */ }
 * backgrounder.start()
 * ```
 *
 * @param tickIdentifier the iOS `BGAppRefreshTaskRequest` identifier the
 *   library uses to wake the periodic dispatcher in the background. Required.
 *   Must appear in your app's `Info.plist` under
 *   `BGTaskSchedulerPermittedIdentifiers` — pick something in your app's
 *   reverse-DNS namespace (e.g. `"com.example.app.background-tick"`). Even
 *   though it's used internally by Backgrounder, the identifier lives in
 *   *your* namespace because it surfaces in *your* Info.plist; the library
 *   never invents identifiers in your namespace for you. Validated at
 *   [Backgrounder.start] time and reported with a Kermit error if missing.
 *
 *   Periodic tasks ([WorkRequest.Periodic]) no longer need per-`TaskId`
 *   Info.plist entries — the tick identifier is the only entry they need.
 *   One-shot tasks ([WorkRequest.OneTime]) still register per-`TaskId` and
 *   still need their own Info.plist entries.
 *
 * @param eventListener observability hook for `onScheduled`, `onStarted`,
 *   `onCompleted`, `onCancelled`. Defaults to [BackgrounderEventListener.Noop].
 *
 * @return a constructed but not-yet-started [Backgrounder]. Call
 *   [Backgrounder.register] for every task id, then
 *   [Backgrounder.start] before the launch method returns.
 *
 * `@OptIn(ExperimentalObjCName::class)`: required by SKIE for the
 * Swift-rename annotation. Stable in practice.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "create")
public fun Backgrounder.Companion.create(
    tickIdentifier: String,
    eventListener: BackgrounderEventListener = BackgrounderEventListener.Noop,
): Backgrounder = IOSBackgrounderBuilder.build(tickIdentifier, eventListener, Reachability.shared)

/**
 * Test / Kotlin-only overload that takes an explicit [Reachability] instance.
 *
 * Hidden from the Swift / Obj-C surface via `@HiddenFromObjC` — exposing the
 * `Reachability` protocol through Backgrounder's framework conflicts with the
 * `reachable` library's bundled `Reachability+Shared.swift` extension (which
 * is authored against the un-namespaced `Reachability` type and breaks when
 * SKIE re-namespaces transitively-imported Kotlin types to
 * `BackgrounderReachableReachability`). Hiding the parameter keeps the
 * type out of Backgrounder's generated Swift module, while leaving the
 * Kotlin call site available for unit tests.
 *
 * Production iOS apps should call the parameter-less overload above, which
 * uses [Reachability.shared] internally.
 *
 * @param reachability the [Reachability] instance the pre-execution network
 *   gate consults to honour `WorkConstraints.networkRequired`. Override with
 *   a fake in tests; production should use the parameter-less overload.
 */
@OptIn(ExperimentalObjCName::class, ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun Backgrounder.Companion.create(
    tickIdentifier: String,
    eventListener: BackgrounderEventListener = BackgrounderEventListener.Noop,
    reachability: Reachability,
): Backgrounder = IOSBackgrounderBuilder.build(tickIdentifier, eventListener, reachability)
