# Schedule a one-shot

A one-shot job runs once. It survives process death and reboot (unless `ephemeral = true`), and is retried per `BackoffPolicy.maxAttempts` if the worker returns `WorkResult.Retry`.

```kotlin
import com.happycodelucky.backgrounder.*
import kotlin.time.Duration.Companion.seconds

val outcome = backgrounder.scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        constraints = WorkConstraints(
            networkRequired = NetworkRequirement.Any,
            requiresCharging = false,
        ),
        initialDelay = 30.seconds,
        backoff = BackoffPolicy.exponential(
            initialDelay = 30.seconds,
            maxAttempts = 5,
        ),
    ),
)

when (outcome) {
    ScheduleOutcome.Scheduled -> Unit
    is ScheduleOutcome.Rejected -> log.e { "rejected: ${outcome.reason}" }
}
```

## What can go wrong

- **iOS Info.plist** — every `TaskId` you schedule must appear in `BGTaskSchedulerPermittedIdentifiers`. The library reports a Kermit error during `backgrounder.start()` if it's missing; rejected at `schedule()` time with `ScheduleOutcome.Rejected`.
- **Constraints conflict on iOS** — `NetworkRequirement.Unmetered` is logged-and-ignored (iOS has no metered/unmetered distinction).
- **Backoff policy with too-small initial delay** — minimum is 10 seconds, validated at construction.

## Conflict policy

If a one-shot with the same `TaskId` is already pending:

```kotlin
backgrounder.scheduler.schedule(request, policy = ConflictPolicy.Replace) // default — cancel pending, enqueue new
backgrounder.scheduler.schedule(request, policy = ConflictPolicy.Keep)    // ignore new, keep pending
```

`Append` (Android chained-work semantics) is v2.
