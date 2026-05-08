package dev.backgrounder

import android.app.Application
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import dev.backgrounder.android.AndroidBackgrounderBuilder
import dev.backgrounder.android.AndroidBackgrounderInternals

/**
 * Android factory for [Backgrounder].
 *
 * Hold the returned instance as a property on your `Application` subclass.
 * The standard wiring is:
 *
 * ```kotlin
 * class MyApp : Application(), Configuration.Provider {
 *     lateinit var backgrounder: Backgrounder
 *
 *     override fun onCreate() {
 *         super.onCreate()
 *         backgrounder = Backgrounder.create(application = this)
 *         backgrounder.register(SyncWorker.ID) { SyncWorker(repo = …) }
 *         backgrounder.start()
 *     }
 *
 *     override val workManagerConfiguration: Configuration get() =
 *         Configuration.Builder()
 *             .setWorkerFactory(backgrounder.androidWorkerFactory())
 *             .build()
 * }
 * ```
 *
 * Plan §"DI-free initialization" §1.3 / §2.3 for the full sequencing
 * argument and the `Configuration.Provider` chicken-and-egg note.
 *
 * @param application your `Application`. The library uses it for
 *   `getSharedPreferences`, the ephemeral sweep, and (lazily)
 *   `WorkManager.getInstance(application)`.
 * @param eventListener observability hook. Defaults to
 *   [BackgrounderEventListener.Noop].
 * @param workManager an optional pre-resolved `WorkManager` instance. Use this
 *   only if you have one already and want to skip the lazy
 *   `WorkManager.getInstance(application)` lookup. Most callers leave this
 *   `null` and let the library resolve via `getInstance` on first use.
 */
public fun Backgrounder.Companion.create(
    application: Application,
    eventListener: BackgrounderEventListener = BackgrounderEventListener.Noop,
    workManager: WorkManager? = null,
): Backgrounder =
    AndroidBackgrounderBuilder.build(
        application = application,
        eventListener = eventListener,
        suppliedWorkManager = workManager,
    )

/**
 * The `WorkerFactory` to install in your `WorkManager.Configuration`. Compose
 * with `DelegatingWorkerFactory` if you also use Hilt's `HiltWorkerFactory` or
 * any other custom factory.
 *
 * ```kotlin
 * override val workManagerConfiguration: Configuration get() =
 *     Configuration.Builder()
 *         .setWorkerFactory(backgrounder.androidWorkerFactory())
 *         .build()
 * ```
 *
 * Or chained with Hilt:
 * ```kotlin
 * Configuration.Builder()
 *     .setWorkerFactory(DelegatingWorkerFactory().apply {
 *         addFactory(hiltWorkerFactory)
 *         addFactory(backgrounder.androidWorkerFactory())
 *     })
 *     .build()
 * ```
 *
 * @throws IllegalStateException if the [Backgrounder] was constructed
 *   from outside [Backgrounder.Companion.create] (e.g. directly via
 *   `Backgrounder(core)` — not normally possible since the constructor
 *   is `internal`, but tests sometimes find a way).
 */
public fun Backgrounder.androidWorkerFactory(): WorkerFactory = AndroidBackgrounderInternals.workerFactory(this)
