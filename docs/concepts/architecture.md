# Architecture

Backgrounder is three layers, all in `/backgrounder`:

```text
┌────────────────────────────────────────────────────────────────────────┐
│  commonMain — public API                                               │
│    BackgroundWorker, WorkerContext, WorkRequest, WorkResult            │
│    Scheduler interface     (schedule / cancel / scheduled / guarantees)│
│    InstantRunner interface (runNow / cancelInFlight)                   │
│    WorkerRegistry — task-id → factory map (the DI seam)                │
│    EphemeralRegistry — cold-launch sweep mirror                        │
│    Backgrounder — class with create / register / start / runNow /      │
│                   cancel / shutdown                                    │
└────────────────────────────────────────────────────────────────────────┘
        ▲                          ▲                            ▲
┌───────┴────────────┐ ┌───────────┴──────────┐ ┌───────────────┴────────┐
│  androidMain       │ │  iosMain             │ │  macosMain             │
│  WorkManager-      │ │  BGTaskBacked-       │ │  NSBackground-         │
│   Scheduler        │ │   Scheduler          │ │  ActivityBacked-       │
│  RegistryDispatch  │ │  + state store       │ │   Scheduler            │
│  Worker            │ │  + coroutine bridge  │ │                        │
│                    │ │  + foreground/       │ │                        │
│                    │ │    background feeds  │ │                        │
│  + WorkManager-    │ │                      │ │  + LibraryScope-       │
│    InstantRunner   │ │  + UIBackgroundTask- │ │    InstantRunner       │
│                    │ │    InstantRunner     │ │                        │
└────────────────────┘ └──────────────────────┘ └────────────────────────┘
```

ARM-only targets — `iosArm64`, `iosSimulatorArm64`, Android `arm64-v8a`, `macosArm64`. No Catalyst, no x86, no watchOS, no tvOS.

## Two surfaces, one entry point

`Backgrounder` exposes **two** parallel dispatch surfaces and a shared cancel:

- **`Scheduler`** (`backgrounder.scheduler`) — OS-backed scheduled work. `schedule(WorkRequest)`, `cancel(taskId)`, `cancelAll()`, `scheduled()`, `guarantees()`. Honors `WorkConstraints`, `BackoffPolicy`, retries. Workers come from the registry. See [Schedule a one-shot](../recipes/one-shot.md) / [Periodic](../recipes/periodic.md).
- **`runNow<R>(taskId, task)`** — instant dispatch. Suspends until the typed result is back. Bypasses constraints, backoff, retries, and the registry — the lambda *is* the work. Routed through the platform's real background primitive (`beginBackgroundTask` on iOS, `WorkManager` on Android, library scope on macOS). See [Run now](../recipes/run-now.md).
- **`cancel(taskId)`** on `Backgrounder` itself is the unified cancel — it kills both scheduled and in-flight `runNow` for the given `TaskId`. `Scheduler.cancel(taskId)` keeps its narrow scheduled-only meaning.

The two surfaces are independent code paths — a `TaskId` can flow through either or both. They share only the `Backgrounder` lifecycle (`start()` / `shutdown()`) and the cancel surface above.

## Why this shape

- **One public surface, three actuals.** Consumers in `commonMain` write against `Scheduler`, `InstantRunner` (indirectly via `Backgrounder.runNow`), and `BackgroundWorker`. Each platform's `Backgrounder.Companion.create(...)` factory wires up its own implementations behind those interfaces, using plain constructor injection — no DI container required.
- **Workers are factory-built per invocation.** The library never instantiates a worker by reflection; the user registers a `() -> BackgroundWorker` factory at app launch and the library calls it each time the platform fires. This is the `@HiltWorker` model generalised for KMP — see [Worker context & DI](worker-context-and-di.md). (`runNow` is the exception — it uses a caller-supplied lambda directly instead of consulting the registry.)
- **State lives where the platform owns it.** Android persists requests in WorkManager's SQLite; iOS persists library-level retry/state in `NSUserDefaults` via `multiplatform-settings`. macOS holds active schedulers in-memory (the OS doesn't need persistence — `NSBackgroundActivityScheduler` is in-process). `runNow` is fully in-process — no persistence on any platform.
- **Guarantees are honest.** `Scheduler.guarantees()` returns a per-platform truth table; UX should branch on it rather than assume parity. See [Guarantees](guarantees.md). `runNow` makes a different (weaker) set of guarantees — see [Run now](../recipes/run-now.md).
