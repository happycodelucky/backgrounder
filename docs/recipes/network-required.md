# Require a network connection

Set `WorkConstraints.networkRequired` on the request, and the platform holds the worker until the network is up.

```kotlin
backgrounder.schedule(
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

Reachable reports an `isDataMetered: Boolean` axis alongside reachability:

- `isDataMetered = false` — Wi-Fi or ethernet.
- `isDataMetered = true` — cellular, personal hotspots, and on Apple, Low Data Mode (the previous three-state `Metering` enum collapsed to one bit in reachable 0.12.x; "expensive" and "constrained" fold together).

The gate resolves `Unmetered` against `isDataMetered == false`. An iPhone on cellular hotspot has `isReachable = true, isDataMetered = true`; an `Unmetered` request waits past it. This is a fidelity improvement over the older iOS behaviour where `Unmetered` downgraded to `Any`.

On Android, `Unmetered` translates to `NetworkType.UNMETERED` in WorkManager's native gating — same effect as on Apple, just enforced by the OS.

## Reading reachability from inside `execute()`

The library doesn't proxy reachability through `WorkerContext`. If you want to inspect the current state inside a worker — for instance, to defer a large transfer on metered networks without forbidding metered entirely — read `Reachability.shared` directly:

```kotlin
import com.happycodelucky.reachable.Reachability

override suspend fun execute(ctx: WorkerContext): WorkResult {
    val status = Reachability.shared.status.value
    if (status.isDataMetered && payloadBytes > 5_000_000) {
        return WorkResult.Retry // try again on Wi-Fi
    }
    api.upload(payload)
    return WorkResult.Success
}
```

`Reachability.shared` is the same singleton the gate consults, so tests' `withFakeReachability { }` install affects both the gate **and** any worker reads transparently — no extra plumbing required. If you need the live `StateFlow` for a long-running worker that wants to react to mid-task transitions, `Reachability.shared.status.collect { … }` works too — workers run on a coroutine.

The hard requirement (`NetworkRequirement.Unmetered`) and the soft worker-side check are complementary: declare the strict version on the request when "no metered, ever" is the answer; inspect `Reachability.shared` when policy is conditional on payload size, attempt count, or anything else only the worker knows.

## Testing

The library reads `Reachability.shared` directly — there is no Backgrounder-side test seam. Tests use the `com.happycodelucky.reachable:reachable-testing` artifact's `withFakeReachability { … }` helper, which temporarily installs a `FakeReachability` as `Reachability.shared` for the duration of the block:

```kotlin
// commonTest — depends on libs.reachable.testing.
@Test fun retriesWhenOffline() = runTest {
    withFakeReachability(initial = ReachabilityStatus.Unknown) { fake ->
        val backgrounder = Backgrounder.create(
            tickIdentifier = "com.example.app.tick",
            eventListener = events,
        )

        // Drive transitions deterministically — `emit(...)`, `setReachable(...)`,
        // `setTransport(...)`, `setDataMetered(...)` are all on the upstream FakeReachability.
        fake.emit(ReachabilityStatus(isReachable = true, transport = Transport.Wifi, isDataMetered = false))
        // ...
    }
}
```

`withFakeReachability` restores the previous override (typically the production singleton) on exit, even on exception. Nested calls are LIFO-safe by construction. See the [reachable-testing module](https://github.com/happycodelucky/reachable/tree/main/reachable-testing) for the full driver API (`setReachable`, `setTransport`, `setDataMetered`, `reset`, `closeCallCount`, `wasClosed`).

Backgrounder's public `Backgrounder.create(...)` factory has no `reachability:` parameter on either platform — the install hook is the *only* path. This keeps the Swift surface clean (no reachable types leak into Backgrounder's framework) and matches how every other consumer of `Reachability.shared` is tested.

## Common pitfalls

- **Don't probe the network from inside `execute()`** if you've set `networkRequired = Any`. The gate has already waited; if it timed out you'll be running with `WorkResult.Retry` not invoked at all, so the body never even ran. Trust the gate.
- **`requiresCharging` is not honoured on Apple.** `WorkConstraints.requiresCharging` only works on Android (via WorkManager). On iOS / macOS the library would need a separate power-state library to wait for charging; that's out of scope today.
- **The gate is per-invocation, not per-schedule.** Each time the OS fires a worker, the gate runs again with that fire's budget. A flaky network produces a sequence of `Retry`s rather than one long hang.
