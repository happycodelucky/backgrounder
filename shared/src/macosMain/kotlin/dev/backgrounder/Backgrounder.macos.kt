package dev.backgrounder

import dev.backgrounder.macos.MacOSBackgrounderBuilder
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * macOS factory for [BackgrounderInstance].
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
 * @return a constructed but not-yet-started [BackgrounderInstance]. Call
 *   [BackgrounderInstance.register] for every task id, then
 *   [BackgrounderInstance.start] from `applicationDidFinishLaunching`.
 *
 * Call [BackgrounderInstance.shutdown] from `applicationWillTerminate` to
 * cancel the scheduler's coroutine scope cleanly.
 *
 * `@OptIn(ExperimentalObjCName::class)`: required by SKIE for the
 * Swift-rename annotation. Stable in practice.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "create")
public fun BackgrounderInstance.Companion.create(
    eventListener: BackgrounderEventListener = BackgrounderEventListener.Noop,
): BackgrounderInstance = MacOSBackgrounderBuilder.build(eventListener)
