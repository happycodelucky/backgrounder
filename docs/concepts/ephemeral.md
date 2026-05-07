# The `ephemeral` flag

## What it solves

A persisted background job can fire **before your app's DI graph is ready** — especially on Android, where `JobScheduler` may dispatch a worker as soon as constraints are satisfied, including during the brief window between the OS launching your process and `Application.onCreate` finishing.

Without protection, the worker calls `GlobalContext.get<MyRepo>()` against a half-initialised Koin container, or worse, runs with stale state from a previous version of the app schema. Either is a foot-gun.

`WorkRequest.ephemeral = true` declares: *this work must be re-scheduled by app code after init; do not run it from a state I didn't deliberately put it in.*

## Contract

- On every cold start of the app process, the library erases all ephemeral entries before the platform scheduler has a chance to dispatch them.
- The user re-schedules ephemeral jobs from app code at a known-safe initialisation point.
- Non-ephemeral jobs are unaffected — they continue to be persisted and survive process death normally.

## Sweep timing per platform

| Platform | "Cold start" means                              | Sweep timing                            |
| -------- | ----------------------------------------------- | --------------------------------------- |
| Android  | `Application.onCreate` is running for a fresh process. | First line of `Backgrounder.attachTo(application)` (called *before* `startKoin`). |
| iOS      | A new process invocation of `application(_:didFinishLaunchingWithOptions:)`. | Top of `Backgrounder.registerHandlers()` (before any `BGTaskScheduler.register(...)`). |
| macOS    | A new process invocation of `applicationDidFinishLaunching`. | Top of `Backgrounder.registerHandlers()` (same as iOS). |

## Android backstop

Even with the sweep, there's a tiny window on Android where `JobScheduler` could fire a worker between the OS launching your process and `Backgrounder.attachTo` running. The library defends against this with a process-local `AtomicBoolean` (`AndroidEphemeralReady`):

- `Backgrounder.attachTo()` resets it to `false`.
- `Backgrounder.markReady()` flips it to `true` — call this once Koin and any further app-side init the workers depend on is complete.
- Inside `RegistryDispatchWorker.doWork()`, ephemeral requests check the flag; if `false`, the worker returns `WorkResult.Failure("dispatched before ephemeralReady")` immediately without invoking user code.

## When to use it

- Worker depends on app state initialised after `Application.onCreate` (typical: anything resolved through Koin singletons that themselves require initialised dependencies).
- Worker reads from a database whose schema may have changed across app updates and you'd rather have user code re-schedule with the migrated state.
- Worker uses input data whose interpretation has changed across app versions.

When **not** to use it: workers that are genuinely self-contained (e.g. POSTing a queued event payload to a server) — those should survive process death and reboot, and `ephemeral = false` is the right default.

```kotlin
scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        ephemeral = true,         // re-scheduled after init each cold start
    ),
)
```
