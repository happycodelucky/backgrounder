# macOS launch sequence

```swift
@main
class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        // 1. Start Koin with backgrounderCommonModule + backgrounderMacOSModule.
        KoinKt.doInitKoin(/* + backgrounderMacOSModule */)

        // 2. Register every worker factory.
        let registry = KoinPlatformKt.getKoin().get(WorkerRegistry.self)
        registry.register(taskId: SyncWorker.companion.ID) {
            SyncWorker(repo: /* injected */)
        }

        // 3. registerHandlers — seals the registry and sweeps ephemeral state.
        BackgrounderRuntime.shared.registerHandlers()
    }
}
```

`NSBackgroundActivityScheduler` owns scheduling lifetime entirely, so unlike iOS there's no per-cold-launch handler-registration ceremony. `registerHandlers()` does just two things on macOS:

1. Sweep ephemeral state.
2. Seal the `WorkerRegistry` so further `register()` calls throw.

## What runs where

- `NSBackgroundActivityScheduler.scheduleWithBlock` invokes a closure on a system queue.
- The library bounces into a `SupervisorJob` + `Dispatchers.Default` scope (the same pattern as iOS), runs the worker, maps `WorkResult` to `NSBackgroundActivityResultFinished` (Success / Failure) or `NSBackgroundActivityResultDeferred` (Retry).
- `cancel(taskId)` calls `invalidate()` on the live scheduler — interrupts the running block. `cancelsInFlight = true`.

## Periodic is native

macOS doesn't need the iOS periodic-emulation state machine. `NSBackgroundActivityScheduler` has `repeats = true`, `interval`, and `tolerance` (mapped from `WorkRequest.Periodic.flexWindow`). The library hands the OS a single repeating activity per task id and lets it dispatch.

## Force-quit isn't a problem

Unlike iOS, force-quitting a macOS app doesn't disable background scheduling for the next launch. The active schedulers re-establish themselves when the user re-launches the app and `registerHandlers()` runs again.
