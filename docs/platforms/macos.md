# macOS launch sequence

```swift
@main
final class AppDelegate: NSObject, NSApplicationDelegate {
    // 1. Construct.
    let backgrounder = Backgrounder.companion.create()

    func applicationDidFinishLaunching(_ notification: Notification) {
        // 2. Register every worker factory.
        backgrounder.register(taskId: SyncWorker.companion.ID) {
            SyncWorker(repo: AppGraph.shared.repository)
        }

        // 3. Start — sweeps ephemeral state and seals the registry.
        backgrounder.start()
    }

    func applicationWillTerminate(_ notification: Notification) {
        // Cancel the scheduler's coroutine scope cleanly.
        backgrounder.shutdown()
    }
}
```

`NSBackgroundActivityScheduler` owns scheduling lifetime entirely, so unlike iOS there's no per-cold-launch handler-registration ceremony. `start()` does just two things on macOS:

1. Sweep ephemeral state.
2. Seal the `WorkerRegistry` so further `register()` calls throw.

## What runs where

- `NSBackgroundActivityScheduler.scheduleWithBlock` invokes a closure on a system queue.
- The library bounces into a `SupervisorJob` + `Dispatchers.Default` scope (the same pattern as iOS), runs the worker, maps `WorkResult` to `NSBackgroundActivityResultFinished` (Success / Failure) or `NSBackgroundActivityResultDeferred` (Retry).
- `cancel(taskId)` calls `invalidate()` on the live scheduler — interrupts the running block. `cancelsInFlight = true`.

## Periodic is native

macOS doesn't need the iOS periodic-emulation state machine. `NSBackgroundActivityScheduler` has `repeats = true`, `interval`, and `tolerance` (mapped from `WorkRequest.Periodic.flexWindow`). The library hands the OS a single repeating activity per task id and lets it dispatch.

## Force-quit isn't a problem

Unlike iOS, force-quitting a macOS app doesn't disable background scheduling for the next launch. The active schedulers re-establish themselves when the user re-launches the app and `backgrounder.start()` runs again.

## Shutdown

`backgrounder.shutdown()` cancels the scheduler's `SupervisorJob`-rooted scope. Call from `applicationWillTerminate` to tear down cleanly. Without it, in-flight workers continue until the OS reclaims the process — for a foreground app being explicitly quit, that's a few extra seconds of work that never matters; for a long-lived agent it can leave file handles open. Always pair with `applicationWillTerminate`.
