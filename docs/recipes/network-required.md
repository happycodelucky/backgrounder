# Require a network connection

Set `WorkConstraints.networkRequired` on the request, and the platform holds the worker until the network is up.

```kotlin
scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        constraints = WorkConstraints(
            networkRequired = NetworkRequirement.Any,
        ),
        backoff = BackoffPolicy.exponential(initialDelay = 30.seconds),
    ),
)
```

Three values:

- `NetworkRequirement.None` — no network check. Worker fires whenever the platform thinks it should.
- `NetworkRequirement.Any` — wait for *any* reachable network (Wi-Fi, ethernet, cellular).
- `NetworkRequirement.Unmetered` — wait specifically for an unmetered network (Wi-Fi or ethernet). Cellular and personal-hotspot connections do **not** satisfy this.

## What each platform does

| Platform | Mechanism | Wait timeout |
| --- | --- | --- |
| Android | Native — `WorkManager` refuses to dispatch the worker until the constraint is met. The OS holds the worker indefinitely. | OS-managed (no library timeout). |
| iOS | Library-managed pre-execution gate. The bridge reads reachability via the [`reachable`](https://github.com/happycodelucky/reachable) library and waits up to `min(5s, capabilities.maxExecutionTime / 4)` for the requirement to become true. | Capped at 5 s; budget-derived (≈30 s App Refresh → 5 s, several-minute BGProcessing → 5 s). |
| macOS | Same library-managed gate as iOS. `NSBackgroundActivityScheduler` has no constraint concept; the library fills the gap. | 5 s cap (5-minute generous budget → 5 s). |

On Apple platforms, if the network never comes up inside the gate's window, the worker is **not invoked** — the bridge short-circuits to `WorkResult.Retry` and the scheduler reschedules per the request's `BackoffPolicy`. The user's `execute()` body never sees an offline state caused by the constraint.

## Why a 5-second cap on Apple

Apple's background-task primitives give us a finite wall-clock budget:

- `BGAppRefreshTaskRequest` — about 30 seconds.
- `BGProcessingTaskRequest` — "several minutes" (the library hints 5).
- In-process foreground feed — unbounded (`Duration.INFINITE`), but the gate caps at 5 s anyway so a flaky network doesn't hold the dispatcher loop.

The formula `min(5.seconds, budget / 4)` quarters the budget so a network gate never burns most of an App Refresh runway, then clamps at 5 s for the long-budget cases. Tasks that need to wait longer than that should return `WorkResult.Retry` themselves — the scheduler's backoff handles the rescheduling cleanly.

## `Unmetered` semantics

Reachable reports a `Metering` value alongside reachability:

- `Metering.Unmetered` — Wi-Fi or ethernet.
- `Metering.Metered` — cellular, including a personal hotspot tethered over Wi-Fi from another iPhone.
- `Metering.Constrained` — Low Data Mode (Apple only). Treated as **not** Unmetered — the gate keeps waiting.

The gate only resolves `Unmetered` against `Metering.Unmetered`. An iPhone on cellular hotspot has `reachable=true, metering=Metered`; an `Unmetered` request waits past it. This is a fidelity improvement over the older behaviour where iOS downgraded `Unmetered` to `Any`.

On Android, `Unmetered` translates to `NetworkType.UNMETERED` in WorkManager's native gating — same effect as on Apple, just enforced by the OS.

## Testing

For pure-logic tests of code that depends on the gate, inject a `FakeReachability` via the parameterised builder overload:

```kotlin
// commonTest — uses the shared FakeReachability helper.
val fake = FakeReachability.offline()
val backgrounder = Backgrounder.create(
    tickIdentifier = "com.example.app.tick",
    eventListener = events,
    reachability = fake,
)

// Drive transitions deterministically:
fake.emitOnline(transport = Transport.Wifi, metering = Metering.Unmetered)
```

The `reachability` parameter is `@HiddenFromObjC` — it's a Kotlin-side test seam, not part of the Swift surface. Production iOS / macOS apps call the parameter-less overload, which uses `Reachability.shared` internally.

## Common pitfalls

- **Don't probe the network from inside `execute()`** if you've set `networkRequired = Any`. The gate has already waited; if it timed out you'll be running with `WorkResult.Retry` not invoked at all, so the body never even ran. Trust the gate.
- **`requiresCharging` is not honoured on Apple.** `WorkConstraints.requiresCharging` only works on Android (via WorkManager). On iOS / macOS the library would need a separate power-state library to wait for charging; that's out of scope today.
- **The gate is per-invocation, not per-schedule.** Each time the OS fires a worker, the gate runs again with that fire's budget. A flaky network produces a sequence of `Retry`s rather than one long hang.
