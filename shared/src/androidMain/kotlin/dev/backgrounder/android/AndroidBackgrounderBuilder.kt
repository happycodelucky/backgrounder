package dev.backgrounder.android

import android.app.Application
import android.content.Context
import androidx.work.WorkManager
import com.russhwolf.settings.SharedPreferencesSettings
import dev.backgrounder.BackgrounderCore
import dev.backgrounder.BackgrounderEventListener
import dev.backgrounder.BackgrounderInstance
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.WorkerRegistry

/**
 * Constructor-injection wiring for the Android [BackgrounderInstance] graph.
 *
 * Replaces the Koin module wiring in `backgrounderAndroidModule` (plan §"DI-free
 * initialization" §2.3). Two notable differences from the iOS / macOS builders:
 *
 *  1. The `WorkManager` resolution is **lazy** — eager-resolving here would
 *     trigger `WorkManager.getInstance(context)` before the user's
 *     `Configuration.Provider.workManagerConfiguration` had a chance to
 *     install our [BackgrounderWorkerFactory]. The provider is called on
 *     every `schedule` / `cancel` instead.
 *  2. The ephemeral sweep runs **eagerly** at construction time — it must
 *     happen before any worker can dispatch (plan §2.3 "eager ephemeral sweep").
 *     This replaces the old `Backgrounder.attachTo(application)` call site.
 *
 * The builder also resets the [AndroidEphemeralReady] singleton (idempotent;
 * safe across multiple `Backgrounder.create(...)` calls in the same process —
 * which shouldn't happen in production but does happen in tests).
 */
internal object AndroidBackgrounderBuilder {
    fun build(
        application: Application,
        eventListener: BackgrounderEventListener,
        suppliedWorkManager: WorkManager?,
    ): BackgrounderInstance {
        val settings =
            SharedPreferencesSettings(
                application.getSharedPreferences("backgrounder.prefs", Context.MODE_PRIVATE),
            )
        val ephemeral = EphemeralRegistry(settings)
        val registry = WorkerRegistry()

        // Eager ephemeral sweep — first thing we do, before any worker can fire.
        // (Replaces the Koin-era `Backgrounder.attachTo(application)` step.)
        AndroidEphemeralSweep(application, ephemeral).run()

        // Reset the ready gate. The singleton is shared with the legacy
        // wiring path during the transition (Steps 1–4); both paths flip it
        // true via `start()` / `Backgrounder.markReady()`.
        AndroidEphemeralReady.reset()

        // Lazy: do NOT call WorkManager.getInstance(application) here. If we
        // did, it would lock the WorkManager configuration to whatever's
        // currently registered — but the user installs our factory via
        // `Configuration.Provider`, which only runs the FIRST time anyone
        // calls `getInstance`. Resolve at use-time instead.
        val workManagerProvider: () -> WorkManager = { suppliedWorkManager ?: WorkManager.getInstance(application) }

        val scheduledTaskQuery = AndroidScheduledTaskQuery.withProvider(workManagerProvider, ephemeral)
        val scheduler =
            WorkManagerScheduler.withProvider(
                workManagerProvider = workManagerProvider,
                ephemeral = ephemeral,
                eventListener = eventListener,
                scheduledTaskQuery = scheduledTaskQuery,
            )
        val factory = BackgrounderWorkerFactory(registry, eventListener)

        val backgrounder =
            BackgrounderInstance(
                BackgrounderCore(
                    registry = registry,
                    scheduler = scheduler,
                    onStart = {
                        // Plan §1.1: `start()` flips the ready gate so workers
                        // that were enqueued (but blocked by the
                        // ephemeral-not-ready backstop) can now run.
                        AndroidEphemeralReady.markReady()
                    },
                    onShutdown = {
                        // No-op on Android — WorkManager owns its own dispatch
                        // scope. Plan §1.1 / SchedulerGuarantees.
                    },
                ),
            )
        AndroidBackgrounderInternals.attach(backgrounder, factory)
        return backgrounder
    }
}
