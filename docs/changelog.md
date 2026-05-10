# Changelog

## Unreleased

### iOS periodic dispatch

- `WorkRequest.Periodic` is now driven by a coalescing dispatcher with two feeds — an in-process loop while the app is foregrounded, and a single library-owned `BGAppRefreshTaskRequest` while it is not. iOS suppresses `BGAppRefreshTaskRequest` for foregrounded apps, so the in-process loop is what fires periodics at the right moment during user sessions; without it a periodic whose interval elapsed during a long session would silently slip past until the user backgrounded the app.
- Foreground-initiated dispatch is wrapped in `UIApplication.beginBackgroundTaskWithName` runway so work that gets backgrounded mid-execution gets the OS-granted continuation window before being treated as `Retry`.
- Coalescing-by-`TaskId` is an explicit cross-platform contract (documented on `WorkRequest.Periodic` and in [iOS launch sequence](platforms/ios.md)). If iOS doesn't dispatch for several intervals, the worker fires once on the next wake — never N times back-to-back to "catch up." Workers that need catch-up logic compute it from their own persisted state (e.g. `lastSyncedAt`).
- `Backgrounder.create(tickIdentifier:)` takes a required tick identifier on iOS. Pick a string in your app's reverse-DNS namespace (e.g. `"<your.bundle.id>.background-tick"`) and add it to `BGTaskSchedulerPermittedIdentifiers` in `Info.plist`. Periodic task ids do not need their own `Info.plist` entries — the tick handles them. One-shot task ids (`WorkRequest.OneTime`) still register per-id and still need their own entries.
- `WorkConstraints` on `WorkRequest.Periodic` are not honored on iOS — App Refresh has no constraint fields; the in-process loop has no constraint concept. Workers that need power/network gating should check at the start of `execute()` and return `WorkResult.Retry`. `WorkConstraints` are honored for `WorkRequest.OneTime` on iOS, and on Android / macOS for both kinds.

### v1 surface

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
