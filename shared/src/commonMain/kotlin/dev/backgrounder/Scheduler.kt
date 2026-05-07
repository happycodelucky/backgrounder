package dev.backgrounder

import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Schedules and inspects background work.
 *
 * Platform actuals:
 * - Android: `WorkManagerScheduler` (backed by Jetpack `WorkManager`).
 * - iOS:     `BGTaskBackedScheduler` (backed by `BGTaskScheduler`).
 * - macOS:   `NSBackgroundActivityBackedScheduler` (backed by Foundation's
 *            `NSBackgroundActivityScheduler`).
 *
 * Get the platform's instance from Koin (`get<Scheduler>()`). Most methods are
 * non-suspend because both `WorkManager.enqueue` and `BGTaskScheduler.submit`
 * are non-blocking. [scheduled] is `suspend` because Android's
 * `WorkManager.getWorkInfos` returns a `ListenableFuture` and iOS's
 * `BGTaskScheduler.getPendingTaskRequests` is callback-shaped.
 */
@OptIn(ExperimentalObjCName::class)
public interface Scheduler {
    /**
     * Schedule a [WorkRequest]. If a request with the same [WorkRequest.taskId]
     * is already pending, [policy] decides what happens.
     */
    @ObjCName(swiftName = "schedule")
    public fun schedule(
        request: WorkRequest,
        policy: ConflictPolicy = ConflictPolicy.Replace,
    ): ScheduleOutcome

    /**
     * Cancel the pending request for [taskId]. Does not interrupt an
     * already-running worker on iOS — see [SchedulerGuarantees.cancelsInFlight].
     */
    @ObjCName(swiftName = "cancel")
    public fun cancel(taskId: TaskId): CancelOutcome

    /** Cancel every pending request the library knows about. */
    @ObjCName(swiftName = "cancelAll")
    public fun cancelAll(): CancelOutcome

    /**
     * Snapshot of currently-scheduled (pending or running) tasks the library
     * knows about. Not a [Flow] — for a reactive stream, see `Scheduler.observe`
     * (v2). Best-effort per platform.
     */
    @ObjCName(swiftName = "scheduled")
    @Throws(CancellationException::class)
    public suspend fun scheduled(): List<ScheduledTask>

    /** What this platform's scheduler actually guarantees. */
    @ObjCName(swiftName = "guarantees")
    public fun guarantees(): SchedulerGuarantees
}
