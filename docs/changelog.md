# Changelog

## Unreleased

First public artifact in preparation. The v1 surface is feature-complete:

- Constructed-instance `Backgrounder` entry point. Three-step launch: `Backgrounder.create(...)` → `register(taskId, factory)` → `start()`. Hold one instance per app for the lifetime of the process.
- **No DI container required.** Factory closures resolve dependencies from whatever DI graph the consumer already uses (Koin, Hilt, kotlin-inject, hand-wired); the library itself ships zero DI dependency.
- Cross-platform `Scheduler` API with `schedule` / `cancel` / `cancelAll` / `scheduled()` / `guarantees()`. Reach via `backgrounder.scheduler`.
- Sealed `WorkRequest`: `OneTime` and `Periodic`, both with `ephemeral` flag.
- `BackoffPolicy` (Linear / Exponential) with `maxAttempts`.
- `ExecutionHint`: `Standard` and `Expedited(QuotaPolicy)`.
- `WorkInput` typed key/value bag, capped at 10 240 bytes.
- Cold-launch ephemeral sweep with platform-appropriate timing + per-instance Android ready-gate backstop.
- iOS periodic emulation via library-internal state machine, with force-quit resurrection.
- macOS native periodic via `NSBackgroundActivityScheduler`.
- Per-platform `SchedulerGuarantees` for honest UX branching.
- Android: hand-rolled `BackgrounderWorkerFactory` that consumers install via `Configuration.Provider.workManagerConfiguration`. Composes with Hilt's `HiltWorkerFactory` via `DelegatingWorkerFactory`.
- MkDocs Material documentation site with Dokka API reference.

## v2 roadmap (not yet)

- Reactive `Scheduler.observe()` Flow (cross-platform).
- `ExecutionHint.LongRunning` for Android `setForeground` / foreground-service work.
- Android-only constraints (storage-not-low, device-idle, content URI triggers) in a `WorkConstraints.Android` extension.
- Published `:testing` artifact with stable, public `FakeScheduler`.

The latest version of this changelog lives at the [GitHub Releases page](https://github.com/happycodelucky/backgrounder/releases) once published.
