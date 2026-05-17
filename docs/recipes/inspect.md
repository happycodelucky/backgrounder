# Inspect scheduled work

`scheduled()` returns a snapshot of every task the library currently knows about.

```kotlin
val tasks: List<ScheduledTask> = backgrounder.scheduled()

tasks.forEach { task ->
    println("${task.taskId}  kind=${task.kind}  state=${task.state}  attempt=${task.attempt}")
    task.pendingPredicates.forEach { predicate ->
        println("  waiting on: $predicate")
    }
}
```

The call is `suspend` because Android's `WorkManager.getWorkInfos` returns a `ListenableFuture` and iOS's `BGTaskScheduler.getPendingTaskRequests` is callback-shaped.

## Fields on `ScheduledTask`

| Field               | Type                   | Meaning                                                                  |
|---------------------|------------------------|--------------------------------------------------------------------------|
| `taskId`            | `TaskId`               | The stable reverse-DNS identifier this task was scheduled under.         |
| `kind`              | `Kind`                 | `OneTime` or `Periodic`.                                                 |
| `state`             | `State`                | `Pending`, `Running`, `Backoff`, or `Blocked`. Best-effort per platform. |
| `nextRunHint`       | `Instant?`             | Platform's best-effort estimate of the next run. May be `null`.          |
| `attempt`           | `Int`                  | Library-tracked retry counter (within a cycle for periodic tasks).       |
| `ephemeral`         | `Boolean`              | `true` if the task was scheduled with `WorkRequest.ephemeral = true`.    |
| `pendingPredicates` | `List<PendingPredicate>` | Conditions currently blocking dispatch. Empty when nothing is blocking. |

## Per-platform state mapping

- **Android.** `state` maps directly from `WorkInfo`: `RUNNING` → `Running`; `ENQUEUED` with `runAttemptCount == 0` → `Pending`; `ENQUEUED` with `runAttemptCount > 0` → `Backoff`; `BLOCKED` → `Blocked`.
- **iOS.** The library does not observe a worker between handler-fire and `setTaskCompletedSuccess`, so that span reports as `Pending` rather than `Running`. `Backoff` means the state store is active, iOS has no pending request, and the attempt counter is non-zero.
- **macOS.** `Backoff` when the previous attempt returned `WorkResult.Retry`; otherwise `Pending`. `nextRunHint` is always `null` — `NSBackgroundActivityScheduler` does not surface a wall-clock run time.

## `pendingPredicates` — why a task isn't running

`ScheduledTask.pendingPredicates` answers "what is the OS waiting for?" without a separate query.

```kotlin
tasks.forEach { task ->
    task.pendingPredicates.forEach { predicate ->
        when (predicate) {
            is PendingPredicate.NetworkRequired ->
                println("  needs network: ${predicate.requirement}")
            PendingPredicate.RequiresCharging ->
                println("  waiting for external power")
            is PendingPredicate.WaitingForBackoff ->
                println("  backoff until ${predicate.until}")
            is PendingPredicate.WaitingForEarliestBeginDate ->
                println("  earliest begin ${predicate.at}")
        }
    }
}
```

Multiple predicates can appear simultaneously — for example, a task in backoff that also has a network requirement shows both `WaitingForBackoff` and `NetworkRequired`.

## Per-platform predicate accuracy

Not every predicate is observable from every platform's state model.

| Predicate                       | Android | iOS | macOS |
|---------------------------------|---------|-----|-------|
| `NetworkRequired`               | Yes — from `WorkInfo.constraints` | Yes — persisted in the state store | No — constraints aren't retained after `NSBackgroundActivityScheduler` is built |
| `RequiresCharging`              | Yes — from `WorkInfo.constraints` | No — not yet persisted (v2, requires state-store schema bump) | No — `NSBackgroundActivityScheduler` has no charging constraint |
| `WaitingForBackoff`             | Yes     | Yes | Yes — but `until` is always `null` |
| `WaitingForEarliestBeginDate`   | Yes     | Yes | No |

!!! warning "iOS"
    `RequiresCharging` is not surfaced on iOS. The charging flag is not currently persisted in the iOS state store; this is a v2 follow-up gated on a schema bump. If your task has `WorkConstraints.requiresCharging = true`, the predicate is invisible on iOS.

!!! warning "macOS"
    macOS only surfaces `WaitingForBackoff` — and only when the previous attempt returned `WorkResult.Retry`. Original constraints (`WorkConstraints`) are not retained once `NSBackgroundActivityScheduler` is built.

## Registered factories

`registeredFactories()` returns one `FactoryDescriptor` per registered factory — useful for inspector attribution.

```kotlin
backgrounder.registeredFactories().forEach { descriptor ->
    when (descriptor) {
        is FactoryDescriptor.PerId ->
            // Single closure registered via backgrounder.register(taskId, factory)
            println("anonymous → ${descriptor.taskId}")
        is FactoryDescriptor.Bulk ->
            // BackgroundWorkerFactory object registered via backgrounder.register(factory)
            println("${descriptor.factoryId ?: "<unnamed>"} → ${descriptor.taskIds}")
    }
}
```

`FactoryDescriptor.PerId` carries `factoryId = null` — per-id closures have no name. `FactoryDescriptor.Bulk` mirrors `BackgroundWorkerFactory.factoryId`, which may also be `null` if the factory didn't set one.

`registeredTaskIds()` gives the same task IDs without the attribution structure:

```kotlin
val ids: Set<TaskId> = backgrounder.registeredTaskIds()
```

## Platform diagnostics

`diagnostics()` reports environment and configuration issues that could prevent scheduled work from running. Run it at app launch as a health check.

```kotlin
val diag: PlatformDiagnostics = backgrounder.diagnostics()

if (!diag.isHealthy) {
    diag.diagnostics.forEach { issue ->
        when (issue) {
            is PlatformDiagnostic.MissingInfoPlistEntry ->
                log.e { "Info.plist missing ${issue.taskId.value}" }
            PlatformDiagnostic.BackgroundRefreshDisabled ->
                log.w { "Background App Refresh is disabled" }
            PlatformDiagnostic.WorkManagerNotInitialized ->
                log.e { "WorkManager is not initialized" }
            PlatformDiagnostic.RegistryNotSealed ->
                log.e { "backgrounder.start() has not been called" }
        }
    }
}
```

Which diagnostic cases each platform can produce:

| Diagnostic                    | Android | iOS | macOS |
|-------------------------------|---------|-----|-------|
| `RegistryNotSealed`           | Yes     | Yes | Yes   |
| `MissingInfoPlistEntry`       | No      | Yes | No    |
| `BackgroundRefreshDisabled`   | No      | Defined but not currently emitted — see note | No |
| `WorkManagerNotInitialized`   | Yes     | No  | No    |

!!! warning "iOS"
    `BackgroundRefreshDisabled` is defined but `diagnostics()` does not currently emit it. Reading `UIApplication.backgroundRefreshStatus` requires the main thread; `diagnostics()` is synchronous and callable from any thread. A future `suspend fun diagnostics()` can hop to the main dispatcher to read it safely.

## Reactive event stream

`scheduled()` is a point-in-time snapshot. For continuous observation, combine it with the event stream from `backgrounder.events()`:

```kotlin
backgrounder.events()
    .filterIsInstance<MonitorEvent.WorkCompleted>()
    .collect { e -> println("${e.taskId} finished in ${e.runtime}: ${e.result}") }
```

The event stream is a `SharedFlow<MonitorEvent>` — hot, non-replaying, `DROP_OLDEST` under back-pressure. See [Monitor lifecycle events](monitor.md) for the full event vocabulary and the optional `:background-monitor` module.

## What can go wrong

- **`scheduled()` returns an empty list too early** — `backgrounder.start()` must be called before scheduling. `diagnostics()` returns `RegistryNotSealed` until `start()` runs.
- **`state` reported as `Pending` for a running iOS task** — iOS does not expose a running-vs-pending signal between handler-fire and `setTaskCompletedSuccess`. This is expected.
- **`pendingPredicates` is empty but the task hasn't fired** — system throttling (thermal pressure, low battery, power nap) is not represented as a predicate. Check the system log for `BGTaskScheduler` or `NSBackgroundActivityScheduler` entries.
- **`diagnostics()` shows `MissingInfoPlistEntry`** — add the task id string to `BGTaskSchedulerPermittedIdentifiers` in your app's `Info.plist`. Without it, iOS will silently refuse to register the OS handler.
- **`diagnostics()` shows `WorkManagerNotInitialized`** — wire `BackgrounderWorkerFactory` into `WorkManager`'s `Configuration.Provider.workManagerConfiguration` before the first `backgrounder.start()`.

## See also

- [Monitor lifecycle events](monitor.md)
- [Cancel work](cancel.md)
