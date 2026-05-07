@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios

import co.touchlab.kermit.Logger
import dev.backgrounder.TaskId
import dev.backgrounder.WorkerRegistry
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle

/**
 * Backs `Backgrounder.registerHandlers()` on iOS. Run from
 * `application(_:didFinishLaunchingWithOptions:)` after Koin starts and after
 * every worker factory is registered.
 *
 * Does, in order:
 *  1. Validates each registered task id appears in `BGTaskSchedulerPermittedIdentifiers`
 *     in the app's `Info.plist`. Missing ids produce a Kermit error.
 *  2. Calls `BGTaskScheduler.register(forTaskWithIdentifier:using:launchHandler:)`
 *     for each registered task id, dispatching to [IOSCoroutineBridge.handle].
 *  3. Resurrects active periodic schedules — for any task id whose state record
 *     has `kind=periodic` and `active=true`, if iOS has no pending request for
 *     it, re-submits one. (Force-quit + relaunch recovery.)
 *  4. Seals the [WorkerRegistry] so further [WorkerRegistry.register] calls
 *     throw — at this point launch has begun in earnest.
 */
internal class BGTaskHandlerRegistration(
    private val registry: WorkerRegistry,
    private val state: IOSStateStore,
    private val bridge: IOSCoroutineBridge,
) {
    private val log = Logger.withTag("Backgrounder/iOS/Registration")

    fun run() {
        val ids = registry.registeredIds()
        if (ids.isEmpty()) {
            log.w { "registerHandlers() called with no factories registered; nothing to do" }
            return
        }
        validatePlistIdentifiers(ids)
        ids.forEach(::registerOne)
        resurrectActivePeriodics()
        registry.seal()
    }

    private fun validatePlistIdentifiers(ids: Set<TaskId>) {
        val permitted = NSBundle.mainBundle.objectForInfoDictionaryKey(PLIST_KEY)
            ?.let { @Suppress("UNCHECKED_CAST") (it as? List<String>) }
            ?.toSet()
            .orEmpty()
        if (permitted.isEmpty()) {
            log.e {
                "Info.plist key '$PLIST_KEY' is missing or empty. " +
                    "Add every Backgrounder task id under this key, or BGTaskScheduler will refuse to dispatch them."
            }
            return
        }
        ids.filter { it.value !in permitted }.forEach { missing ->
            log.e {
                "task id '${missing.value}' is not in '$PLIST_KEY'; iOS will refuse to dispatch it. " +
                    "Add it to Info.plist."
            }
        }
    }

    private fun registerOne(taskId: TaskId) {
        val ok = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = taskId.value,
            usingQueue = null,
        ) { task: BGTask? ->
            val real = task ?: run {
                log.e { "BGTaskScheduler launch handler called with null task for $taskId" }
                return@registerForTaskWithIdentifier
            }
            bridge.handle(real, taskId)
        }
        if (!ok) {
            log.w { "BGTaskScheduler.register(forTaskWithIdentifier:) returned false for $taskId" }
        }
    }

    private fun resurrectActivePeriodics() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        state.knownTaskIds()
            .filter { state.readKind(it) == IOSStateStore.Kind.Periodic && state.readActive(it) }
            .forEach { id ->
                val intervalMs = state.readIntervalMs(id) ?: return@forEach
                val lastRunMs = state.readLastRunEpochMs(id) ?: nowMs
                val nextRunMs = maxOf(nowMs, lastRunMs + intervalMs)
                val req = BGProcessingTaskRequest(id.value).apply {
                    earliestBeginDate = epochMsToNSDate(nextRunMs)
                }
                try {
                    BGTaskScheduler.sharedScheduler.submitTaskRequest(req, error = null)
                    state.setNextRunEpochMs(id, nextRunMs)
                    log.i { "resurrected periodic $id; next run at ${nextRunMs}ms" }
                } catch (t: Throwable) {
                    log.e(t) { "failed to resurrect periodic $id" }
                }
            }
    }

    internal companion object {
        internal const val PLIST_KEY: String = "BGTaskSchedulerPermittedIdentifiers"
    }
}
