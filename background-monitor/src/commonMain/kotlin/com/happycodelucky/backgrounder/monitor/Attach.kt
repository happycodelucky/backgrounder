package com.happycodelucky.backgrounder.monitor

import com.happycodelucky.backgrounder.Backgrounder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Attach a [Monitor] to [Backgrounder.events]. The collector coroutine
 * runs on [scope]; cancelling either [scope] or the returned
 * [AttachedMonitor] tears the subscription down.
 *
 * **Scope ownership** (CLAUDE.md §3). The collector is a child of [scope],
 * so cancellation propagates through structured concurrency. Pick a scope
 * tied to a sensible lifetime — typically a ViewModel scope, an
 * app-level scope, or a test's scope. Never `GlobalScope`.
 *
 * **Multiple monitors.** Each call attaches an independent collector;
 * monitors do not share state or back-pressure. The core's
 * `MutableSharedFlow` is hot and non-replaying — late attachers see
 * events emitted after their attach point, never before.
 *
 * **Cancellation.** If [Monitor.onEvent] throws or [scope] is cancelled,
 * the collector terminates. Throws inside [onEvent] propagate via the
 * collector's job and are surfaced through the scope's exception handler
 * (typically logged via Kermit if the scope's CoroutineExceptionHandler
 * routes there).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(swiftName = "attach")
public fun Backgrounder.attachMonitor(
    scope: CoroutineScope,
    monitor: Monitor,
): AttachedMonitor {
    val job =
        scope.launch {
            events().collect { event ->
                monitor.onEvent(event)
            }
        }
    return AttachedMonitor(job)
}
