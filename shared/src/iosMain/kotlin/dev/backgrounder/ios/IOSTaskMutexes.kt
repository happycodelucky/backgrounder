package dev.backgrounder.ios

import dev.backgrounder.TaskId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-[TaskId] [Mutex] map.
 *
 * Concurrency in iOS scheduling: BGTaskScheduler may fire two distinct task
 * identifiers concurrently, and the user's code can call
 * `Scheduler.schedule` for one task id while another instance's handler is
 * mid-flight. Every read-modify-write of `tasks.<id>.*` is wrapped in
 * `withMutex(taskId)` so we don't lose a state transition to a race.
 */
internal class IOSTaskMutexes {
    private val lock = SynchronizedObject()
    private val mutexes: MutableMap<TaskId, Mutex> = mutableMapOf()

    suspend inline fun <T> withMutex(
        taskId: TaskId,
        crossinline block: suspend () -> T,
    ): T = mutexFor(taskId).withLock { block() }

    fun mutexFor(taskId: TaskId): Mutex =
        synchronized(lock) {
            mutexes.getOrPut(taskId) { Mutex() }
        }
}
