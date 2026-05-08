package dev.backgrounder

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The DI seam: maps stable [TaskId]s to factories that build a fresh
 * [BackgroundWorker] per invocation.
 *
 * Each factory is a lambda that closes over the user's DI graph (typically
 * resolving from Koin's `GlobalContext`). The library calls the factory each
 * time the platform fires a worker — never caches the worker instance itself.
 *
 * Register every factory at app launch *before* `Backgrounder.registerHandlers`
 * (iOS / macOS) or `Backgrounder.markReady` (Android). Re-registering the same
 * id throws.
 */
public class WorkerRegistry {
    // MUST NOT call suspend functions inside this block — see CLAUDE.md §3.
    private val lock = SynchronizedObject()
    private val factories: MutableMap<TaskId, () -> BackgroundWorker> = mutableMapOf()
    private val sealed = atomic(false)

    /**
     * Associate [taskId] with a [factory] that builds a fresh [BackgroundWorker] per dispatch.
     *
     * Must be called before [Backgrounder.registerHandlers] (iOS / macOS) or
     * [Backgrounder.markReady] (Android). Throws if the registry is already sealed or
     * [taskId] was already registered.
     *
     * @throws IllegalStateException if the registry is sealed.
     * @throws IllegalArgumentException if [taskId] is already registered.
     */
    public fun register(
        taskId: TaskId,
        factory: () -> BackgroundWorker,
    ): Unit =
        synchronized(lock) {
            check(!sealed.value) {
                "WorkerRegistry has been sealed; register all workers before app launch completes."
            }
            require(taskId !in factories) {
                "WorkerRegistry: task id '$taskId' is already registered."
            }
            factories[taskId] = factory
        }

    /** Returns the set of task ids currently registered. Snapshot — safe to call at any time. */
    public fun registeredIds(): Set<TaskId> = synchronized(lock) { factories.keys.toSet() }

    /**
     * Mark the registry sealed. After this, [register] throws — protects against
     * late registration after the platform has started dispatching work.
     */
    internal fun seal() {
        sealed.value = true
    }

    internal fun create(taskId: TaskId): BackgroundWorker =
        synchronized(lock) {
            val factory =
                factories[taskId]
                    ?: throw NoFactoryRegisteredException(taskId)
            factory()
        }

    public class NoFactoryRegisteredException(
        public val taskId: TaskId,
    ) : IllegalStateException("No BackgroundWorker factory registered for task id '$taskId'")
}
