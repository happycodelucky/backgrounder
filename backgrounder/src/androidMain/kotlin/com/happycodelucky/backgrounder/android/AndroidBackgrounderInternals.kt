package com.happycodelucky.backgrounder.android

import android.app.Application
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.happycodelucky.backgrounder.Backgrounder
import com.happycodelucky.backgrounder.WorkerRegistry
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Slim singleton side-table that pairs each [Backgrounder] with the
 * Android-specific extras the common `Backgrounder` doesn't expose
 * directly: the [BackgrounderWorkerFactory] (which the user composes into
 * their `WorkManager.Configuration`) and the [Application] context used by
 * the diagnostics path to read WorkManager init state.
 *
 * Plan §"DI-free initialization" §2.3 — this side-table is the cost of
 * keeping `Backgrounder` a pure cross-platform type with no Android
 * symbols leaking into commonMain.
 *
 * MUST NOT call suspend functions inside the [synchronized] blocks
 * (CLAUDE.md §3 — non-suspending critical sections only).
 */
internal object AndroidBackgrounderInternals {
    private val lock = SynchronizedObject()
    private val factories: MutableMap<Backgrounder, BackgrounderWorkerFactory> = mutableMapOf()
    private val applications: MutableMap<WorkerRegistry, Application> = mutableMapOf()

    fun attach(
        backgrounder: Backgrounder,
        factory: BackgrounderWorkerFactory,
        application: Application,
        registry: WorkerRegistry,
    ): Unit =
        synchronized(lock) {
            factories[backgrounder] = factory
            applications[registry] = application
        }

    fun workerFactory(backgrounder: Backgrounder): WorkerFactory =
        synchronized(lock) {
            factories[backgrounder]
                ?: error(
                    "Backgrounder has not been attached. " +
                        "This means you constructed it from outside Backgrounder.create(application: ...) — " +
                        "use the create() factory.",
                )
        }

    /**
     * `true` if WorkManager has been initialised for the application paired
     * with [registry], or `null` if we don't have an application reference
     * for that registry (defensive — the diagnostics surface treats null as
     * "can't tell" rather than emitting a false negative).
     */
    fun isWorkManagerInitialized(registry: WorkerRegistry): Boolean? =
        synchronized(lock) {
            val app = applications[registry] ?: return@synchronized null
            // WorkManager.isInitialized() is a Java-side static; the Kotlin
            // accessor exists from androidx.work 2.8+.
            @Suppress("DEPRECATION")
            WorkManager.isInitialized() ||
                runCatching { WorkManager.getInstance(app) }.isSuccess
        }
}
