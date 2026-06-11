// ExperimentalForeignApi: the injected submit seam's signature references the
// cinterop type `BGTaskRequest`. Stable in practice (see BGSubmitResult.kt).
@file:OptIn(ExperimentalForeignApi::class)

package com.happycodelucky.backgrounder.ios

import com.happycodelucky.backgrounder.BackgrounderEventListener
import com.happycodelucky.backgrounder.ConflictPolicy
import com.happycodelucky.backgrounder.EphemeralRegistry
import com.happycodelucky.backgrounder.MonitorEventEmitter
import com.happycodelucky.backgrounder.ReachabilityGate
import com.happycodelucky.backgrounder.ScheduleOutcome
import com.happycodelucky.backgrounder.TaskId
import com.happycodelucky.backgrounder.WorkRequest
import com.happycodelucky.backgrounder.WorkResult
import com.happycodelucky.backgrounder.WorkerRegistry
import com.happycodelucky.reachable.ReachabilityStatus
import com.happycodelucky.reachable.Transport
import com.happycodelucky.reachable.testing.FakeReachability
import com.russhwolf.settings.MapSettings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Anchor for B-023: a failed `BGTaskScheduler.submit` must roll back *both*
 * halves of the speculative schedule — the [IOSStateStore] record AND the
 * [EphemeralRegistry] entry. Before the fix, the failure branch cleared state
 * but left the ephemeral marker, so `scheduled()` / `diagnostics()` reported a
 * ghost task until the next cold-launch sweep.
 *
 * Uses the injected `submitRequest` seam — the real submit needs a live app
 * host, which unit tests don't have (see B-004 / BGSubmitResult.kt).
 */
class BGTaskBackedSchedulerSubmitFailureTest {
    private val taskId = TaskId("com.happycodelucky.backgrounder.test.submitfail")

    private class NoopListener : BackgrounderEventListener {
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

    private class Rig(
        val scheduler: BGTaskBackedScheduler,
        val ephemeral: EphemeralRegistry,
        val store: IOSStateStore,
    )

    private fun newRig(submit: (platform.BackgroundTasks.BGTaskRequest) -> BGSubmitResult): Rig {
        val store = IOSStateStore(MapSettings())
        val mutexes = IOSTaskMutexes()
        val ephemeral = EphemeralRegistry(MapSettings())
        val registry = WorkerRegistry()
        val emitter = MonitorEventEmitter(NoopListener())
        val gate =
            ReachabilityGate(
                FakeReachability(
                    ReachabilityStatus(isReachable = true, transport = Transport.Wifi, isDataMetered = false),
                ),
            )
        val dispatcher =
            IOSPeriodicDispatcher(
                state = store,
                mutexes = mutexes,
                registry = registry,
                ephemeral = ephemeral,
                emitter = emitter,
                gate = gate,
                clock = { 1_767_225_600_000L },
            )
        val scheduler =
            BGTaskBackedScheduler(
                state = store,
                mutexes = mutexes,
                ephemeral = ephemeral,
                emitter = emitter,
                backgroundFeed = IOSBackgroundFeed("com.happycodelucky.backgrounder.test.tick", dispatcher),
                foregroundFeed = IOSForegroundFeed(dispatcher),
                submitRequest = submit,
            )
        return Rig(scheduler, ephemeral, store)
    }

    @Test
    fun submitFailureRollsBackEphemeralEntryAndState() {
        val rig = newRig { BGSubmitResult.Failure("simulated: not permitted") }

        val outcome =
            rig.scheduler.schedule(
                WorkRequest.OneTime(taskId = taskId, ephemeral = true),
                ConflictPolicy.Replace,
            )

        assertIs<ScheduleOutcome.Rejected>(outcome)
        assertTrue(
            rig.ephemeral.snapshot().isEmpty(),
            "failed submit must not leave a ghost ephemeral entry",
        )
        assertFalse(rig.store.readActive(taskId), "failed submit must clear the active state record")
    }

    @Test
    fun submitSuccessKeepsEphemeralEntryAndState() {
        val rig = newRig { BGSubmitResult.Success }

        val outcome =
            rig.scheduler.schedule(
                WorkRequest.OneTime(taskId = taskId, ephemeral = true),
                ConflictPolicy.Replace,
            )

        assertIs<ScheduleOutcome.Scheduled>(outcome)
        assertTrue(taskId in rig.ephemeral.snapshot(), "successful submit keeps the ephemeral entry")
        assertTrue(rig.store.readActive(taskId), "successful submit keeps the active state record")
    }
}
