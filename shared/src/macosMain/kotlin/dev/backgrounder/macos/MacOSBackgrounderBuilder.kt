// ExperimentalForeignApi: required by `NSUserDefaults(suiteName:)` cinterop call.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.macos

import com.russhwolf.settings.NSUserDefaultsSettings
import dev.backgrounder.BackgrounderCore
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.BackgrounderInstance
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.WorkerRegistry
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/**
 * Constructor-injection wiring for the macOS [BackgrounderInstance] graph.
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
    fun build(eventListener: BackgrounderEventListener): BackgrounderInstance {
        val settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "dev.backgrounder.shared"))
        val ephemeral = EphemeralRegistry(settings)
        val registry = WorkerRegistry()

        val scheduler =
            NSBackgroundActivityBackedScheduler(
                registry = registry,
                ephemeral = ephemeral,
                eventListener = eventListener,
            )

        return BackgrounderInstance(
            BackgrounderCore(
                registry = registry,
                scheduler = scheduler,
                onStart = {
                    // macOS has no OS-level "registered task ids" concept; the
                    // ephemeral sweep just clears our mirror. (Plan §2.2.)
                    val ids = ephemeral.snapshot()
                    if (ids.isNotEmpty()) ephemeral.clear()
                },
                onShutdown = { scheduler.shutdown() },
            ),
        )
    }
}
