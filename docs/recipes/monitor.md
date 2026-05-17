# Monitor lifecycle events

Every schedule, dispatch, deferral, completion, retry, cancellation, and library-internal error fans out through one stream.

```kotlin
backgrounder.events()
    .collect { event ->
        when (event) {
            is MonitorEvent.Scheduled        -> log.i { "scheduled ${event.taskId}" }
            is MonitorEvent.ScheduleReplaced -> log.i { "replaced ${event.taskId} (policy=${event.policy})" }
            is MonitorEvent.WorkStarted      -> log.i { "started ${event.taskId} attempt=${event.attempt}" }
            is MonitorEvent.WorkCompleted    -> log.i { "completed ${event.taskId} in ${event.runtime}: ${event.result}" }
            is MonitorEvent.AttemptDeferred  -> log.w { "deferred ${event.taskId}: ${event.reason}" }
            is MonitorEvent.AttemptFailed    -> log.e { "failed ${event.taskId}: ${event.reason}" }
            is MonitorEvent.RetryScheduled   -> log.i { "retry ${event.taskId} attempt=${event.nextAttempt} in ${event.delay}" }
            is MonitorEvent.Cancelled        -> log.i { "cancelled ${event.taskId} via ${event.source}" }
            is MonitorEvent.Skipped          -> log.w { "skipped ${event.taskId}: ${event.reason}" }
            is MonitorEvent.LibraryError     -> log.e(event.cause) { "library error ${event.taskId}: ${event.message}" }
        }
    }
```

`events()` returns a `SharedFlow<MonitorEvent>` backed by `replay = 0, extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST`. Emit is non-suspending (`tryEmit`) — a slow collector cannot block scheduler dispatch. Late collectors see only events emitted after they subscribe; sustained back-pressure drops the oldest unread events first.

Swift sees this as `AsyncSequence<MonitorEvent>` via SKIE. Use `onEnum(of:)` for exhaustive switching.

## All 10 event cases

`MonitorEvent` is a `sealed interface`. Every case carries `taskId: TaskId` and `at: Instant`.

| Case                | When emitted                                                                              |
|---------------------|-------------------------------------------------------------------------------------------|
| `Scheduled`         | A `WorkRequest` was accepted. Carries the full `request`.                                 |
| `ScheduleReplaced`  | A `ConflictPolicy.Replace` displaced an existing request. Emitted just before `Scheduled`. |
| `WorkStarted`       | The platform fired the worker; execution is about to begin. Carries `attempt` (0-based) and `expectedAt?`. |
| `WorkCompleted`     | `BackgroundWorker.execute` returned. Carries `attempt`, `result: WorkResult`, `runtime: Duration`. |
| `AttemptDeferred`   | The worker was deferred without running (reachability timeout, backoff window, no matching tick). Followed by `WorkCompleted(result = Retry)`. |
| `AttemptFailed`     | The attempt failed outside the worker returning `WorkResult.Failure` — OS expiry, factory threw, worker threw. Carries `reason: AttemptFailureReason`. |
| `RetryScheduled`    | The library resubmitted a retry to the platform. Carries `nextAttempt`, `delay`, `nextRunHint?`. |
| `Cancelled`         | The task was removed. Carries `source: CancelSource` (`User`, `Replaced`, or `Shutdown`). |
| `Skipped`           | The platform fired the worker but the library could not run it — no factory, factory declined, or ephemeral wash. No retry follows. |
| `LibraryError`      | A library-internal error that doesn't fit the per-attempt frame. The library has already handled it; this surfaces what would otherwise be Kermit-only. |

The supporting sealed types — `CancelSource`, `DeferralReason`, `SkipReason`, `AttemptFailureReason` — are top-level (not nested) so SKIE bridges them reliably.

## With the `:background-monitor` module

The optional `:background-monitor` artifact adds `Monitor`, `AttachedMonitor`, and `SnapshotPoller`. It removes the need to manage the `collect` coroutine yourself.

```kotlin
// commonMain or build.gradle.kts
implementation("com.happycodelucky.backgrounder:background-monitor:{{ version }}")
```

Implement `Monitor` and attach it to a scope:

```kotlin
class LoggingMonitor : Monitor {
    override suspend fun onEvent(event: MonitorEvent) {
        log.i { event.toString() }
    }
}

val attached: AttachedMonitor = backgrounder.attachMonitor(viewModelScope, LoggingMonitor())
```

`attachMonitor` launches a child coroutine on `viewModelScope`. When the scope cancels (e.g. ViewModel teardown), the subscription tears down automatically. To stop early without cancelling the scope:

```kotlin
attached.detach()          // idempotent — second call is a no-op
val live = attached.isActive  // true while the collector is running
```

Multiple monitors can attach to one `Backgrounder` simultaneously. Each runs its own independent collector coroutine — no shared state, no back-pressure between monitors. The unit of subscription is the `Monitor` instance; the unit of cancellation is the returned `AttachedMonitor`.

## Snapshot polling alongside events

A debug screen typically wants two things at once: the live event stream and a periodically-refreshed snapshot of the current scheduler state. `SnapshotPoller` handles the polling loop and exposes both through `StateFlow`s.

```kotlin
val poller = SnapshotPoller(backgrounder, interval = 1.seconds)
val pollerJob = poller.start(viewModelScope)

// Collect independently — each StateFlow holds the most recent snapshot.
launch { poller.scheduled.collect { tasks -> renderTaskList(tasks) } }
launch { poller.diagnostics.collect { diag -> renderHealthBanner(diag) } }
```

Before the first poll completes, `poller.scheduled` emits `null`. After that, every poll either updates the flow value or leaves it unchanged (`StateFlow` de-duplicates equal values). `poller.diagnostics` starts at `PlatformDiagnostics.Healthy`.

For on-demand refreshes — pull-to-refresh, or reacting to a `WorkCompleted` event so the task list updates immediately rather than on the next interval tick — call `poller.pollNow()`:

```kotlin
poller.pollNow()  // suspend; runs one poll, updates both StateFlows, returns.
```

`pollNow()` is additive: it does not reset the interval cadence, and it works whether `start()` has been called or not (so a screen that only wants on-demand polling can construct the poller and skip `start()` entirely).

Combine the interval loop with a `Monitor` for a debug screen that shows both real-time events and refreshed state:

```kotlin
class DebugScreenViewModel(backgrounder: Backgrounder) : ViewModel() {
    private val poller = SnapshotPoller(backgrounder, interval = 1.seconds)

    // Stable snapshot of all scheduled tasks — refreshed every second.
    val tasks: StateFlow<List<ScheduledTask>?> = poller.scheduled

    // Stable snapshot of environment health.
    val diagnostics: StateFlow<PlatformDiagnostics> = poller.diagnostics

    // Real-time event log — caller collects and appends to their own list.
    val events: SharedFlow<MonitorEvent> = backgrounder.events()

    init {
        poller.start(viewModelScope)
        attachMonitor(viewModelScope, object : Monitor {
            override suspend fun onEvent(event: MonitorEvent) {
                // Trigger an immediate poll on task completion so the task list
                // reflects the finished state without waiting for the next tick.
                // pollNow() is additive — the interval loop's cadence is unchanged.
                if (event is MonitorEvent.WorkCompleted) poller.pollNow()
            }
        })
    }

    override fun onCleared() {
        poller.stop()
    }
}
```

Don't poll faster than approximately 250 ms. The Android `WorkInfo` query is IPC-bound; the iOS `BGTaskScheduler.getPendingTaskRequests` is callback-shaped. Both calls block a thread while awaiting the platform response.

## What can go wrong

- **Slow `Monitor.onEvent`** — the collector coroutine pauses while `onEvent` runs. Heavy work (HTTP, database writes) inside `onEvent` can let the 64-slot buffer fill under burst load, causing the oldest unread events to be dropped. Forward to a `Channel` and drain it in a separate coroutine if your work is slow.
- **Throwing `Monitor.onEvent`** — an uncaught exception propagates through the collector's job and terminates the subscription. Catch and log inside `onEvent` for any work that can fail.
- **Late attach sees nothing historical** — the `SharedFlow` does not replay. If you need past events (for example, a debug screen that opens after dispatch has already happened), persist events as they arrive in your own buffer.
- **No synthetic `Cancelled` event on `Backgrounder.shutdown()`** — shutdown cancels the library's internal coroutine scopes. Collectors observing `events()` see the flow stop emitting without a per-task `Cancelled` event. If your consumer needs a "library is shutting down" signal, watch for the collecting scope's cancellation directly.
- **Events arrive out of order across task ids** — within one task id, events follow natural order (`Scheduled` → `WorkStarted` → `WorkCompleted`). Cross-task ordering reflects the producer's interleave and is not deterministic.

## See also

- [Inspect scheduled work](inspect.md)
- [Cancel work](cancel.md)
