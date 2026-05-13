package com.happycodelucky.backgrounder

import kotlin.time.Duration

/**
 * What the platform-currently-running-this-worker can do.
 *
 * Read by workers via [WorkerContext.capabilities] to make platform-aware
 * decisions (e.g. "I have ~30s on iOS Expedited; checkpoint every 5s").
 */
public data class PlatformCapabilities(
    /**
     * Approximate wall-clock budget the platform gives this invocation.
     * - Android Standard: ~10 minutes.
     * - Android Expedited: ~10 minutes (foreground job).
     * - iOS Expedited (`BGAppRefreshTaskRequest`): ~30 seconds.
     * - iOS Standard (`BGProcessingTaskRequest`): "several minutes".
     * - macOS: system-determined; we report a conservative estimate.
     *
     * Not enforced by the library — it's a hint for worker code to budget against.
     */
    val maxExecutionTime: Duration,
    /**
     * `true` if `Scheduler.cancel(taskId)` interrupts an already-running worker.
     * - Android: `true` (coroutine cancellation via `onStopped`).
     * - macOS: `true` (`NSBackgroundActivityScheduler.invalidate()`).
     * - iOS: `false` — `BGTaskScheduler.cancel(_:)` only kills *pending* requests.
     */
    val cancelsInFlight: Boolean,
)
