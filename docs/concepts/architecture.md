# Architecture

Backgrounder is three layers, all in `/shared`:

```text
┌──────────────────────────────────────────────────────────────────┐
│  commonMain — public API                                         │
│    BackgroundWorker, WorkerContext, WorkRequest, WorkResult      │
│    Scheduler interface (schedule / cancel / scheduled / guarantees) │
│    WorkerRegistry — task-id → factory map (the DI seam)          │
│    EphemeralRegistry — cold-launch sweep mirror                  │
│    Backgrounder facade — attachTo / registerHandlers / markReady │
└──────────────────────────────────────────────────────────────────┘
        ▲                      ▲                      ▲
┌───────┴────────────┐ ┌───────┴────────┐ ┌───────────┴────────┐
│  androidMain       │ │  iosMain       │ │  macosMain         │
│  WorkManager-      │ │  BGTaskBacked- │ │  NSBackground-     │
│   Scheduler        │ │   Scheduler    │ │  ActivityBacked-   │
│  RegistryDispatch  │ │  + state store │ │   Scheduler        │
│  Worker            │ │  + coroutine   │ │                    │
│                    │ │   bridge       │ │                    │
└────────────────────┘ └────────────────┘ └────────────────────┘
```

Per CLAUDE.md §1: ARM-only targets — `iosArm64`, `iosSimulatorArm64`, Android `arm64-v8a`, `macosArm64`. No Catalyst, no x86, no watchOS, no tvOS.

## Why this shape

- **One public surface, three actuals.** Consumers in `commonMain` write against `Scheduler` and `BackgroundWorker`. Each platform binds its own `Scheduler` implementation through Koin, hidden behind that single interface.
- **Workers are factory-built per invocation.** The library never instantiates a worker by reflection; the user registers a `() -> BackgroundWorker` factory at app launch and the library calls it each time the platform fires. This is the `@HiltWorker` model generalised for KMP — see [Worker context & DI](worker-context-and-di.md).
- **State lives where the platform owns it.** Android persists requests in WorkManager's SQLite; iOS persists library-level retry/state in `NSUserDefaults` via `multiplatform-settings`. macOS holds active schedulers in-memory (the OS doesn't need persistence — `NSBackgroundActivityScheduler` is in-process).
- **Guarantees are honest.** `Scheduler.guarantees()` returns a per-platform truth table; UX should branch on it rather than assume parity. See [Guarantees](guarantees.md).
