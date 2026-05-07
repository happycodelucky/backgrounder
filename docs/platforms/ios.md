# iOS launch sequence

!!! warning "Read the force-quit caveat first"
    iOS background tasks **stop firing entirely** when the user force-quits the app, until they manually launch it again. See [Force-quit caveat (iOS)](force-quit.md). This is the single most-often-misunderstood thing about iOS background work.

`AppDelegate.application(_:didFinishLaunchingWithOptions:)` does **three** things, in this order:

```swift
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions:
            [UIApplication.LaunchOptionsKey: Any]?,
    ) -> Bool {
        // 1. Start Koin with backgrounderCommonModule + backgrounderIOSModule.
        KoinKt.doInitKoin(/* your platform module + Backgrounder modules */)

        // 2. Register every worker factory.
        let registry = KoinPlatformKt.getKoin().get(WorkerRegistry.self)
        registry.register(taskId: SyncWorker.companion.ID) {
            SyncWorker(repo: /* injected */)
        }

        // 3. Register OS handlers — sweeps ephemeral state, registers
        //    BGTaskScheduler launch handlers, and resurrects active periodics.
        BackgrounderRuntime.shared.registerHandlers()
        return true
    }
}
```

`registerHandlers()` must be called **before the launch method returns** — `BGTaskScheduler.register` requires its handler to be registered before the app finishes launching, or iOS will refuse to dispatch tasks for that identifier in this process.

## Info.plist

Add every `TaskId` you schedule:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.sync</string>
    <string>dev.example.app.upload</string>
</array>
```

The library validates this list during `registerHandlers()` and reports a Kermit error per missing id. Failing close to the cause (registration time) rather than first-`schedule()` time means the diagnostic is much easier to act on.

## What runs where

- The OS calls a Swift / Obj-C closure on its own queue when a `BGTask` fires.
- The library bounces into a `SupervisorJob`-rooted `CoroutineScope` on `Dispatchers.Default` (CLAUDE.md §3 forbids `GlobalScope`; the scope is owned by the iOS scheduler).
- `BGTask.expirationHandler` is wired to cancel the coroutine job; `job.invokeOnCompletion` calls `setTaskCompletedWithSuccess(false)` on cancellation. Success path: `applyResult` calls `setTaskCompletedWithSuccess` directly inside the coroutine.

## Concurrency

`BGTaskScheduler` may fire two distinct task identifiers concurrently. Per-task state operations (read attempt, write attempt, mark inactive) go through a `ConcurrentMap<TaskId, Mutex>` — distinct task ids run independently; cross-task state is single-writer-per-key.

## Testing

```
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"dev.example.app.sync"]
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateExpirationForTaskWithIdentifier:@"dev.example.app.sync"]
```

Background tasks **do not fire automatically in the Simulator**. The LLDB simulate-launch hook is private (leading underscore) and only available on Simulator builds. On device, you wait for the system to dispatch.
