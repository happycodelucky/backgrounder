# Guarantees

`Backgrounder.guarantees()` returns a per-platform truth table. UX should branch on it rather than assume parity.

| Field                       | Android `WorkManager` | iOS 18 `BGTaskScheduler` | macOS 15 `NSBackgroundActivityScheduler` |
| --------------------------- | --------------------- | ------------------------ | ---------------------------------------- |
| `survivesProcessDeath`      | true                  | true                     | **false** (in-process)                   |
| `survivesReboot`            | true                  | true                     | **false**                                |
| `survivesForceQuit`         | **true**              | **false**                | **false**                                |
| `honoursWallClock`          | approximate           | **false** (hint only)    | approximate                              |
| `supportsRetryBackoff`      | true (native)         | true (library-emulated)  | true (library-emulated)                  |
| `cancelsInFlight`           | **true**              | **false**                | true                                     |
| `minimumPeriodicInterval`   | 15 min                | 15 min recommended       | 1 sec                                    |
| `maxConcurrentTasks`        | unbounded-ish         | ~1000                    | unbounded-ish                            |

Read carefully:

- **`survivesForceQuit = false` on iOS.** The single most important caveat. See [Force-quit caveat (iOS)](../platforms/force-quit.md).
- **All survival flags are `false` on macOS.** `NSBackgroundActivityScheduler` registrations live in your process — when the process exits (quit, force-quit, reboot), every schedule goes with it, and nothing relaunches the app. Re-schedule from your app's init path at next launch.
- **`honoursWallClock = false` on iOS** means `earliestBeginDate` is a *hint* — the system can defer indefinitely based on opaque heuristics (battery state, usage patterns, Low Power Mode).
- **`cancelsInFlight = false` on iOS** means `Backgrounder.cancel(taskId)` only kills *pending* requests for scheduled work; a worker already executing on iOS finishes whatever it was doing.

## Process death

The contract is the same on every platform:

1. **In-flight work gets to try to complete** — the library never preemptively kills a running worker.
2. **A run interrupted by process death is dead.** The library makes no attempt to restart that transaction. On Android — where WorkManager would otherwise re-run a death-interrupted worker — the library detects the interrupted attempt at the next dispatch and terminates it (`SkipReason.PreviousAttemptDiedWithProcess`). On iOS, a one-shot whose `BGTask` died mid-run is cleared from the state store at the next `start()`. A periodic's *schedule* survives where the platform persists it; only the interrupted cycle is lost (see [missed cycles](../recipes/periodic.md)).
3. **Scheduled-but-never-started work is not "interrupted".** Where the platform persists it (Android WorkManager; iOS pending `BGTaskRequest`s), it may start later in a new process and complete normally.
4. **Ephemeral work is purged** at the next launch before anything can dispatch — see [the `ephemeral` flag](ephemeral.md).

## Branching UX on guarantees

```kotlin
val g = backgrounder.guarantees()

if (!g.survivesForceQuit) {
    // iOS: educate the user.
    showToast("Open the app daily so we can sync.")
}

if (!g.honoursWallClock) {
    // iOS: don't promise a wall-clock cadence.
    label.text = "Syncs throughout the day"      // not "syncs every hour"
}

if (!g.cancelsInFlight) {
    // iOS: a Cancel button only stops *future* runs, not the current one.
    cancelButton.subtitle = "Stops future runs"
}
```

## What's *not* in the table

- **`isForeground`** — there's no notion of "foreground work" in v1 (Android `setForeground` lands as `ExecutionHint.LongRunning` in v2).
- **`requiresCharging` on Apple** — Android honours `WorkConstraints.requiresCharging` natively via WorkManager. On iOS / macOS the library has no charging probe, so the field is silently ignored. Workers that need charging should check inside `execute()` and return `WorkResult.Retry`.
- **Anything observability-related** — `Scheduler.observe()` is v2.

## Network constraints — honoured everywhere

`WorkConstraints.networkRequired` is honoured on every platform, by different mechanisms:

- **Android** — `WorkManager` refuses to dispatch the worker until the constraint is met. The OS holds it indefinitely.
- **iOS / macOS** — a library-managed pre-execution gate, driven by [`reachable`](https://github.com/happycodelucky/reachable), waits up to `min(5 s, budget / 4)`. On timeout the worker is short-circuited to `WorkResult.Retry` and the scheduler reschedules per the request's `BackoffPolicy`.

`NetworkRequirement.Unmetered` is honoured against `ReachabilityStatus.isDataMetered == false` (wifi/ethernet) on all platforms — Android maps to `NetworkType.UNMETERED`; Apple checks the metered axis directly. The legacy "downgrade Unmetered to Any on iOS" behaviour is gone. See [Recipes → Require a network connection](../recipes/network-required.md).
