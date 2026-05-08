package dev.backgrounder

import dev.backgrounder.ios.IOSBackgrounderBuilder
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * iOS factory for [Backgrounder].
 *
 * Hold the returned instance for the lifetime of the app — typically as a
 * stored property on `AppDelegate`. The Swift call site reads:
 *
 * ```swift
 * let backgrounder = Backgrounder.companion.create()
 * backgrounder.register(taskId: SyncWorker.companion.ID) { /* SyncWorker(…) */ }
 * backgrounder.start()
 * ```
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
public fun Backgrounder.Companion.create(eventListener: BackgrounderEventListener = BackgrounderEventListener.Noop): Backgrounder =
    IOSBackgrounderBuilder.build(eventListener)
