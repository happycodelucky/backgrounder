# Force-quit caveat (iOS)

!!! danger "Read before shipping iOS"
    When the user **force-quits the app from the App Switcher**, **all background tasks stop firing entirely** until the user manually launches the app again. Silent push notifications also stop. Background fetch stops. Library-emulated periodic schedules stop. The library cannot work around this — it's Apple's intentional design.

## Why

Apple treats force-quit as an explicit user signal: "I don't want this app running in the background." Across the BackgroundTasks framework, silent pushes, and `URLSession` background transfers, the behaviour is consistent — the system *will not dispatch* anything until the user launches the app themselves.

This is **not** the same as the OS killing your process for memory pressure or system reboot. Those, the library handles fine — see [Architecture](../concepts/architecture.md):

- **Process killed by OS** → state survives in `NSUserDefaults`; on next launch, the resurrection sweep in `backgrounder.start()` re-submits active periodic schedules.
- **Device reboot** → same.
- **User force-quits** → state survives, but **iOS refuses to dispatch handlers** until the user launches the app.

## What surfaces in your UX

`Scheduler.guarantees().survivesForceQuit` is `false` on iOS. Branch on it:

```kotlin
if (!scheduler.guarantees().survivesForceQuit) {
    // iOS-only educational nudge.
    showOnboardingTip(
        title = "Keep notifications fresh",
        body = "Open the app daily so we can sync. Force-quitting from the " +
               "App Switcher pauses background updates until you launch us again.",
    )
}
```

Concrete patterns:

- **Time-sensitive sync** (e.g. "your inbox refreshed at 9 AM"): set the user's expectation to "throughout the day" rather than wall-clock cadence.
- **One-shot uploads**: queue them, but warn "Uploads paused — tap to resume" if the user has force-quit and re-launched.
- **Long-running jobs**: don't promise completion in background. iOS has no equivalent of Android's `setForeground` for arbitrary work; the closest is `BGContinuedProcessingTaskRequest` (iOS 17+, requires foreground init), which is on the v2 roadmap.

## What to *not* do

- **Don't try to detect force-quit.** There's no API for it. Apps that "detect" it via heuristics are observing process termination, not user intent.
- **Don't bypass the limitation with `UIApplication.beginBackgroundTask`.** That's a 30-second extension of the *current* foreground session, not a scheduler.
- **Don't promise reliability.** Even without force-quit, `BGTaskScheduler.earliestBeginDate` is a hint; the system may defer indefinitely based on Low Power Mode, Doze-equivalent heuristics, and the user's recent app-usage patterns.

## What the library does

- Reports `survivesForceQuit = false` from `Scheduler.guarantees()`.
- Logs a Kermit warning at info level if a periodic schedule's expected next-run is more than 24 hours stale on `backgrounder.start()` — likely sign of force-quit-then-relaunch.
- Resurrects active periodic schedules at next cold launch via `BGTaskScheduler.submit`. Force-quit drops the OS's pending-request set; the library re-submits.

## On Android and macOS

Force-quit is **not** an issue:

- **Android.** `survivesForceQuit = true`. `WorkManager` survives force-stop (the user explicitly *force-stopping* an app via Settings does cancel work; that's a separate, more deliberate user action).
- **macOS.** `survivesForceQuit = true`. `NSBackgroundActivityScheduler` re-establishes on the next app launch.

So this whole page is iOS-specific. Read it before shipping; surface it in your UX; move on.
