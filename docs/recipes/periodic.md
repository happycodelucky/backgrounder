# Schedule a periodic

Periodic jobs repeat indefinitely until cancelled. The Android floor is **15 minutes** and is validated at construction; iOS / macOS recommend the same for parity.

```kotlin
import kotlin.time.Duration.Companion.minutes

scheduler.schedule(
    WorkRequest.Periodic(
        taskId = SyncWorker.ID,
        interval = 30.minutes,
        flexWindow = 5.minutes,                    // optional — work runs in the last 5 min of each window
        constraints = WorkConstraints(networkRequired = NetworkRequirement.Any),
    ),
)
```

## How `WorkResult` affects the schedule

| Worker returns        | Periodic semantics                                                     |
| --------------------- | ---------------------------------------------------------------------- |
| `Success`             | Reschedule at `now + interval`. Reset attempt counter to 0.            |
| `Failure(reason)`     | **Reschedule at `now + interval`** — a single failed run does not kill the schedule. Matches WorkManager. |
| `Retry`               | Reschedule at `now + backoff.delayFor(attempt)` (one-time deviation from cadence). The next regular run still follows. |

## iOS specifics

iOS has no native repeating-task primitive — periodic is **library-emulated**:

1. The handler runs the worker.
2. Before calling `setTaskCompletedSuccess`, the library re-submits a fresh `BGTaskRequest` with `earliestBeginDate = now + interval`.
3. State is persisted (`tasks.<id>.kind = "periodic"`, `active = true`, `interval_ms`, `last_run`) so a force-quit + cold-launch path can resurrect the schedule on the next `Backgrounder.registerHandlers()`.

The force-quit caveat still applies — see [Force-quit caveat (iOS)](../platforms/force-quit.md).

## macOS specifics

`NSBackgroundActivityScheduler` is natively repeating, so periodic is *not* emulated on macOS. The `repeats = true` flag plus `interval` and `tolerance` (mapped from `flexWindow`) are all the OS needs.
