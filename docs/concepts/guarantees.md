# Guarantees

`Scheduler.guarantees()` returns a per-platform truth table. UX should branch on it rather than assume parity.

| Field                       | Android `WorkManager` | iOS 18 `BGTaskScheduler` | macOS 15 `NSBackgroundActivityScheduler` |
| --------------------------- | --------------------- | ------------------------ | ---------------------------------------- |
| `survivesProcessDeath`      | true                  | true                     | true                                     |
| `survivesReboot`            | true                  | true                     | true                                     |
| `survivesForceQuit`         | **true**              | **false**                | true                                     |
| `honoursWallClock`          | approximate           | **false** (hint only)    | approximate                              |
| `supportsRetryBackoff`      | true (native)         | true (library-emulated)  | true (library-emulated)                  |
| `cancelsInFlight`           | **true**              | **false**                | true                                     |
| `minimumPeriodicInterval`   | 15 min                | 15 min recommended       | 1 sec                                    |
| `maxConcurrentTasks`        | unbounded-ish         | ~1000                    | unbounded-ish                            |

Read carefully:

- **`survivesForceQuit = false` on iOS.** The single most important caveat. See [Force-quit caveat (iOS)](../platforms/force-quit.md).
- **`honoursWallClock = false` on iOS** means `earliestBeginDate` is a *hint* — the system can defer indefinitely based on opaque heuristics (battery state, usage patterns, Low Power Mode).
- **`cancelsInFlight = false` on iOS** means `Scheduler.cancel(taskId)` only kills *pending* requests; a worker already executing on iOS finishes whatever it was doing.

## Branching UX on guarantees

```kotlin
val g = scheduler.guarantees()

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
- **Per-network-type policies** — Android exposes `NetworkRequirement.Unmetered`; iOS doesn't honour it. The library logs a single Kermit warning when it's set on iOS rather than silently ignoring.
- **Anything observability-related** — `Scheduler.observe()` is v2.
