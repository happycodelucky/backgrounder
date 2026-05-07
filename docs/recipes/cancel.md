# Cancel work

```kotlin
val outcome: CancelOutcome = scheduler.cancel(SyncWorker.ID)

when (outcome) {
    is CancelOutcome.Cancelled -> log.i { "cleared ${outcome.pendingCleared} pending request(s)" }
    CancelOutcome.NoSuchTask    -> log.i { "no such pending task" }
}
```

To cancel everything Backgrounder has scheduled:

```kotlin
scheduler.cancelAll()
```

`cancelAll()` only cancels work this library scheduled (Android: matched by the canonical `_backgrounder` tag; iOS: enumerated from the library's state store). Other WorkManager / `BGTaskScheduler` work in your app is unaffected.

## What "cancel" means per platform

| Platform | `cancel(taskId)` interrupts a running worker? |
| -------- | --------------------------------------------- |
| Android  | **Yes** — `WorkManager.cancelUniqueWork` triggers `onStopped`, the coroutine job is cancelled. |
| iOS      | **No** — `BGTaskScheduler.cancel(taskRequestWithIdentifier:)` only kills *pending* requests. A worker mid-execution finishes whatever it was doing. |
| macOS    | **Yes** — `NSBackgroundActivityScheduler.invalidate()` interrupts the running block. |

The iOS gap is reflected in `Scheduler.guarantees().cancelsInFlight = false`. If your UX shows a "Cancel" button, branch on this:

```kotlin
val cancelButton = if (scheduler.guarantees().cancelsInFlight) {
    Button("Cancel sync")                     // stops in-flight on Android / macOS
} else {
    Button("Cancel future syncs")             // honest about iOS
}
```

## Cancelling a periodic

For periodic schedules, `cancel(taskId)` clears the persisted `active` flag. If a handler is *already running* on iOS, it sees `active = false` on completion and skips the resubmit step — so the next interval won't fire. The current run still completes.
