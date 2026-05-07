# Changelog

## Unreleased

First public artifact in preparation. The v1 surface is feature-complete:

- Cross-platform `Scheduler` API with `schedule` / `cancel` / `cancelAll` / `scheduled()` / `guarantees()`.
- Sealed `WorkRequest`: `OneTime` and `Periodic`, both with `ephemeral` flag.
- `BackoffPolicy` (Linear / Exponential) with `maxAttempts`.
- `ExecutionHint`: `Standard` and `Expedited(QuotaPolicy)`.
- `WorkInput` typed key/value bag, capped at 10 240 bytes.
- DI seam via `WorkerRegistry` + factory closures.
- Cold-launch ephemeral sweep with platform-appropriate timing + Android `markReady` backstop.
- iOS periodic emulation via library-internal state machine, with force-quit resurrection.
- macOS native periodic via `NSBackgroundActivityScheduler`.
- Per-platform `SchedulerGuarantees` for honest UX branching.
- MkDocs Material documentation site with Dokka API reference.

## v2 roadmap (not yet)

- Reactive `Scheduler.observe()` Flow (cross-platform).
- `ExecutionHint.LongRunning` for Android `setForeground` / foreground-service work.
- Android-only constraints (storage-not-low, device-idle, content URI triggers) in a `WorkConstraints.Android` extension.
- Published `:testing` artifact with stable, public `FakeScheduler`.

The latest version of this changelog lives at the [GitHub Releases page](https://github.com/happycodelucky/backgrounder/releases) once published.
