package com.happycodelucky.backgrounder

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Optional lifecycle hook for app-level metrics.
 *
 * Kermit handles structured logging by default. This interface surfaces the
 * same events for "is iOS actually running my tasks?" dashboards. Implementations
 * **must not block or throw** — they're called inline on the dispatcher running
 * the worker.
 *
 * Wire one via the Koin module by binding `single<BackgrounderEventListener>`.
 *
 * `@OptIn(ExperimentalObjCName::class)`: Swift-rename annotation so callbacks
 * read like Swift selectors at the iOS / macOS boundary (CLAUDE.md §8).
 */
@OptIn(ExperimentalObjCName::class)
public interface BackgrounderEventListener {
    /** Called immediately after [Scheduler.schedule] accepts a [WorkRequest]. */
    @ObjCName(swiftName = "onScheduled")
    public fun onScheduled(
        taskId: TaskId,
        request: WorkRequest,
    )

    /**
     * Called when the platform fires the worker and execution is about to begin.
     *
     * @param attempt 0-based retry counter; 0 on the first invocation.
     */
    @ObjCName(swiftName = "onStarted")
    public fun onStarted(
        taskId: TaskId,
        attempt: Int,
    )

    /**
     * Called when [BackgroundWorker.execute] returns (regardless of [WorkResult]).
     *
     * @param attempt 0-based counter matching the value passed to [onStarted].
     * @param result the outcome returned by [BackgroundWorker.execute].
     */
    @ObjCName(swiftName = "onCompleted")
    public fun onCompleted(
        taskId: TaskId,
        attempt: Int,
        result: WorkResult,
    )

    /** Called when [Scheduler.cancel] or [Scheduler.cancelAll] removes this task. */
    @ObjCName(swiftName = "onCancelled")
    public fun onCancelled(taskId: TaskId)

    public companion object {
        /** No-op listener — used as the default when the user binds nothing. */
        public val Noop: BackgrounderEventListener =
            object : BackgrounderEventListener {
                override fun onScheduled(
                    taskId: TaskId,
                    request: WorkRequest,
                ) = Unit

                override fun onStarted(
                    taskId: TaskId,
                    attempt: Int,
                ) = Unit

                override fun onCompleted(
                    taskId: TaskId,
                    attempt: Int,
                    result: WorkResult,
                ) = Unit

                override fun onCancelled(taskId: TaskId) = Unit
            }
    }
}
