package dev.backgrounder.android

import androidx.work.WorkerFactory
import dev.backgrounder.BackgrounderInstance
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Slim singleton side-table that pairs each [BackgrounderInstance] with the
 * Android-specific extras the common `BackgrounderInstance` doesn't expose
 * directly: the [BackgrounderWorkerFactory] (which the user composes into
 * their `WorkManager.Configuration`) and a `dummy` future hook.
 *
 * Plan §"DI-free initialization" §2.3 — this side-table is the cost of
 * keeping `BackgrounderInstance` a pure cross-platform type with no Android
 * symbols leaking into commonMain.
 *
 * MUST NOT call suspend functions inside the [synchronized] blocks
 * (CLAUDE.md §3 — non-suspending critical sections only).
 */
internal object AndroidBackgrounderInternals {
    private val lock = SynchronizedObject()
    private val factories: MutableMap<BackgrounderInstance, BackgrounderWorkerFactory> = mutableMapOf()

    fun attach(
        backgrounder: BackgrounderInstance,
        factory: BackgrounderWorkerFactory,
    ): Unit =
        synchronized(lock) {
            factories[backgrounder] = factory
        }

    fun workerFactory(backgrounder: BackgrounderInstance): WorkerFactory =
        synchronized(lock) {
            factories[backgrounder]
                ?: error(
                    "BackgrounderInstance has not been attached. " +
                        "This means you constructed it from outside Backgrounder.create(application: ...) — " +
                        "use the create() factory.",
                )
        }
}
