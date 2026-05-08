// ExperimentalForeignApi: required by `NSUserDefaults(suiteName:)` cinterop call.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import com.russhwolf.settings.NSUserDefaultsSettings
import dev.backgrounder.Backgrounder
import dev.backgrounder.BackgrounderCore
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.PendingInstantCalls
import dev.backgrounder.WorkerRegistry
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/**
 * Constructor-injection wiring for the iOS [Backgrounder] graph.
 *
 * Replaces the Koin module wiring in `backgrounderIOSModule` (plan §"DI-free
 * initialization" §2.1). Each platform piece is constructed in dependency
 * order; the only forward reference (the [IOSCoroutineBridge.applyResult]
 * lambda needing a [BGTaskBackedScheduler]) is resolved by ordering — the
 * scheduler has no compile-time dependency on the bridge, so it's built
 * first and captured by reference inside the lambda.
 *
 * No Koin, no service lookup, no `lateinit` / `Lazy` — pure constructor
 * injection. The only side-effect at construction time is allocating an
 * `NSUserDefaults` suite (cheap; idempotent).
 */
internal object IOSBackgrounderBuilder {
    fun build(
        tickIdentifier: String,
        eventListener: BackgrounderEventListener,
    ): Backgrounder {
        val settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "dev.backgrounder.shared"))
        val ephemeral = EphemeralRegistry(settings)
        val state = IOSStateStore(settings)
        val mutexes = IOSTaskMutexes()
        val registry = WorkerRegistry()

        // The dispatcher is pure logic — no platform deps. Constructed here
        // so its lifecycle is co-owned with the rest of the iOS graph; the
        // background feed consumes it directly (foreground feed in step 5).
        val dispatcher =
            IOSPeriodicDispatcher(
                state = state,
                mutexes = mutexes,
                registry = registry,
                ephemeral = ephemeral,
                eventListener = eventListener,
            )

        // Background feed — owns the single library tick identifier. In step 4
        // it registers alongside the existing per-id handlers (no behaviour
        // change yet); the cut-over to dispatcher-driven periodics happens in
        // step 6 when BGTaskBackedScheduler.schedulePeriodic switches.
        val backgroundFeed =
            IOSBackgroundFeed(
                tickIdentifier = tickIdentifier,
                dispatcher = dispatcher,
            )

        // Foreground feed — observes UIApplication lifecycle and runs an
        // in-process dispatch loop while the app is foregrounded. UIKit
        // observation lives behind a justification comment in the file
        // header (CLAUDE.md §1 gray zone).
        val foregroundFeed = IOSForegroundFeed(dispatcher = dispatcher)

        // Scheduler depends on both feeds (so it can refresh the tick request
        // and kick the in-process loop after schedule/cancel) but not on the
        // bridge — the bridge captures the scheduler in its applyResult lambda.
        val scheduler =
            BGTaskBackedScheduler(
                state = state,
                mutexes = mutexes,
                ephemeral = ephemeral,
                eventListener = eventListener,
                backgroundFeed = backgroundFeed,
                foregroundFeed = foregroundFeed,
            )

        // Bridge captures the scheduler reference inside the applyResult lambda.
        // Plan §2.1: "the only forward-reference puzzle resolves by ordering".
        val bridge =
            IOSCoroutineBridge(
                registry = registry,
                state = state,
                mutexes = mutexes,
                eventListener = eventListener,
                applyResult = { task, taskId, attempt, result, guard ->
                    scheduler.applyResult(task, taskId, attempt, result, guard)
                },
            )

        val sweep = IOSEphemeralSweep(ephemeral, state)
        // Step 4: BGTaskHandlerRegistration now also takes the background feed, which
        // it uses to register the tick identifier with BGTaskScheduler (alongside
        // per-id handlers — coexistence is intentional until step 6's cut-over).
        val registration =
            BGTaskHandlerRegistration(
                registry = registry,
                state = state,
                bridge = bridge,
                tickIdentifier = tickIdentifier,
                backgroundFeed = backgroundFeed,
            )

        // The instant-runNow path is fully independent of BGTaskScheduler — see
        // UIBackgroundTaskInstantRunner KDoc for why. It is *not* a parallel
        // branch of IOSCoroutineBridge: BGTaskScheduler is the wrong primitive
        // for "do this now and let me await the result", so we use
        // UIApplication.beginBackgroundTask instead.
        val pendingInstantCalls = PendingInstantCalls()
        val instantRunner = UIBackgroundTaskInstantRunner(pendingInstantCalls)

        return Backgrounder(
            BackgrounderCore(
                registry = registry,
                scheduler = scheduler,
                instantRunner = instantRunner,
                onStart = {
                    // Sweep first (clears ephemeral state before any handler fires),
                    // then registration (registers OS handlers, validates plist,
                    // queues the tick request, resurrects active periodics).
                    // `BackgrounderCore.start()` already sealed the registry before
                    // invoking this lambda.
                    //
                    // Foreground feed starts last so its UIApplication-state probe
                    // observes the post-launch state (active vs background); a
                    // launch sequence that synchronously backgrounds the app
                    // before start() returns is rare but not impossible.
                    sweep.run()
                    registration.run()
                    foregroundFeed.start()
                },
                onShutdown = {
                    // Reverse order: tear down the foreground feed first (removes
                    // UIApplication observers), then the background feed (cancels
                    // the per-tick scope), then the bridge (cancels the per-task
                    // scope used for one-shots), then the instant runner (ends
                    // any outstanding UIApplication.beginBackgroundTask runways
                    // and completes pending deferreds with CancellationException).
                    foregroundFeed.shutdown()
                    backgroundFeed.shutdown()
                    bridge.shutdown()
                    instantRunner.shutdown()
                },
            ),
        )
    }
}
