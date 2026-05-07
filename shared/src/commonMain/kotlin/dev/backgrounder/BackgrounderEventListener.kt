package dev.backgrounder

/**
 * Optional lifecycle hook for app-level metrics.
 *
 * Kermit handles structured logging by default. This interface surfaces the
 * same events for "is iOS actually running my tasks?" dashboards. Implementations
 * **must not block or throw** — they're called inline on the dispatcher running
 * the worker.
 *
 * Wire one via the Koin module by binding `single<BackgrounderEventListener>`.
 */
public interface BackgrounderEventListener {
    public fun onScheduled(taskId: TaskId, request: WorkRequest)
    public fun onStarted(taskId: TaskId, attempt: Int)
    public fun onCompleted(taskId: TaskId, attempt: Int, result: WorkResult)
    public fun onCancelled(taskId: TaskId)

    public companion object {
        /** No-op listener — used as the default when the user binds nothing. */
        public val Noop: BackgrounderEventListener = object : BackgrounderEventListener {
            override fun onScheduled(taskId: TaskId, request: WorkRequest) = Unit
            override fun onStarted(taskId: TaskId, attempt: Int) = Unit
            override fun onCompleted(taskId: TaskId, attempt: Int, result: WorkResult) = Unit
            override fun onCancelled(taskId: TaskId) = Unit
        }
    }
}
