# Inspect scheduled work

```kotlin
val tasks: List<ScheduledTask> = scheduler.scheduled()

tasks.forEach { task ->
    println("${task.taskId} → ${task.kind} ${task.state} attempt=${task.attempt}")
}
```

`scheduled()` returns a snapshot — a list of every task the library knows about. It's `suspend` because Android's `WorkManager.getWorkInfos` returns a `ListenableFuture` and iOS's `BGTaskScheduler.getPendingTaskRequests` is callback-shaped; the suspend signature lets us await both cleanly.

## Fields on `ScheduledTask`

| Field          | Meaning                                                                                        |
| -------------- | ---------------------------------------------------------------------------------------------- |
| `taskId`       | The stable `TaskId` you scheduled.                                                             |
| `kind`         | `OneTime` or `Periodic`.                                                                       |
| `state`        | One of `Pending`, `Running`, `Backoff`, `Blocked`. Best-effort per platform — see below.       |
| `nextRunHint`  | Best-effort `Instant` of the next scheduled run. May be `null`.                                |
| `attempt`      | Library-tracked retry attempt counter (within a cycle for periodic).                           |
| `ephemeral`    | True if this task was scheduled with `ephemeral = true`.                                       |

## Per-platform state mapping

- **Android.** `state` is derived directly from `WorkInfo` — `RUNNING` → `Running`, `ENQUEUED` → `Pending` (or `Backoff` if `runAttemptCount > 0`), `BLOCKED` → `Blocked`.
- **iOS.** The library does not directly observe a worker between handler-fire and `setTaskCompletedSuccess`, so that span is reported as `Pending` rather than `Running`. `Backoff` means the library's state store says active + iOS has no pending request + attempt counter is non-zero. `Blocked` is rare (typically force-quit aftermath before the resurrection sweep).
- **macOS.** `Backoff` if the library's last result was `Retry`; otherwise `Pending`.

## Reactive `observe()` (v2)

A `Flow<List<ScheduledTask>>` API is planned for v2. v1 ships only the snapshot reader because making `observe()` honest on iOS requires a synthetic event store; the snapshot is enough for most UX (refresh-on-pull, periodic poll).
