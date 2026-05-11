// ExperimentalForeignApi: required by `NSUserDefaults(suiteName:)` cinterop call.
@file:OptIn(ExperimentalForeignApi::class)

package com.happycodelucky.backgrounder.macos

import com.happycodelucky.backgrounder.Backgrounder
import com.happycodelucky.backgrounder.BackgrounderCore
import com.happycodelucky.backgrounder.BackgrounderEventListener
import com.happycodelucky.backgrounder.EphemeralRegistry
import com.happycodelucky.backgrounder.PendingInstantCalls
import com.happycodelucky.backgrounder.WorkerRegistry
import com.russhwolf.settings.NSUserDefaultsSettings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/**
 * Constructor-injection wiring for the macOS [Backgrounder] graph.
 *
 * Replaces the Koin module wiring in `backgrounderMacOSModule` (plan §"DI-free
 * initialization" §2.2). The macOS graph is shorter than iOS's because
 * `NSBackgroundActivityScheduler` is in-process and doesn't need cold-launch
 * handler registration:
 *
 *  - no state store (the scheduler holds its own activity map),
 *  - no coroutine-bridge object (the scheduler launches its own coroutines),
 *  - no plist-validation step (no Info.plist registry exists on macOS).
 *
 * `start()` only needs to clear ephemeral entries from our mirror;
 * `shutdown()` cancels the scheduler's [kotlinx.coroutines.SupervisorJob]-rooted scope.
 */
internal object MacOSBackgrounderBuilder {
    fun build(eventListener: BackgrounderEventListener): Backgrounder {
        val settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "com.happycodelucky.backgrounder.shared"))
        val ephemeral = EphemeralRegistry(settings)
        val registry = WorkerRegistry()

        val scheduler =
            NSBackgroundActivityBackedScheduler(
                registry = registry,
                ephemeral = ephemeral,
                eventListener = eventListener,
            )

        // The instant runner is owned by Backgrounder, not Scheduler — see plan
        // §"Why a separate runner type rather than reusing Scheduler". macOS's
        // runner runs the lambda directly on a library scope; no platform
        // scheduler is involved.
        val pendingInstantCalls = PendingInstantCalls()
        val instantRunner = LibraryScopeInstantRunner(pendingInstantCalls)

        return Backgrounder(
            BackgrounderCore(
                registry = registry,
                scheduler = scheduler,
                instantRunner = instantRunner,
                onStart = {
                    // macOS has no OS-level "registered task ids" concept; the
                    // ephemeral sweep just clears our mirror. (Plan §2.2.)
                    val ids = ephemeral.snapshot()
                    if (ids.isNotEmpty()) ephemeral.clear()
                },
                onShutdown = {
                    scheduler.shutdown()
                    instantRunner.shutdown()
                },
            ),
        )
    }
}
