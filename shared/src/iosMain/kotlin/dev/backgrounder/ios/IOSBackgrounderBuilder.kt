// ExperimentalForeignApi: required by `NSUserDefaults(suiteName:)` cinterop call.
@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import com.russhwolf.settings.NSUserDefaultsSettings
import dev.backgrounder.Backgrounder
import dev.backgrounder.BackgrounderCore
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.EphemeralRegistry
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
    fun build(eventListener: BackgrounderEventListener): Backgrounder {
        val settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "dev.backgrounder.shared"))
        val ephemeral = EphemeralRegistry(settings)
        val state = IOSStateStore(settings)
        val mutexes = IOSTaskMutexes()
        val registry = WorkerRegistry()

        // Build scheduler first — it has no dependency on the bridge.
        val scheduler =
            BGTaskBackedScheduler(
                state = state,
                mutexes = mutexes,
                ephemeral = ephemeral,
                eventListener = eventListener,
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
        val registration = BGTaskHandlerRegistration(registry, state, bridge)

        return Backgrounder(
            BackgrounderCore(
                registry = registry,
                scheduler = scheduler,
                onStart = {
                    // The order matches the old `BackgrounderIOS.platformRegisterHandlers`:
                    // sweep first (clears ephemeral state before any handler fires),
                    // then registration (registers OS handlers, validates plist,
                    // resurrects active periodics). `BackgrounderCore.start()` already
                    // sealed the registry before invoking this lambda.
                    sweep.run()
                    registration.run()
                },
                onShutdown = { bridge.shutdown() },
            ),
        )
    }
}
