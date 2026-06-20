# Opportunistic dispatch

Background work in Backgrounder is **opportunistic**, not scheduled-to-the-minute. You describe *what* should run and *under what conditions*; the OS decides *when* — based on battery, connectivity, recent app usage, thermal state, and its own system-wide budget. This is the single most-misunderstood thing about background work on every platform, so read this before you reason about timing.

!!! tip "The one-sentence model"
    An interval is a **floor, not a promise.** "Every 30 minutes" means *"no more often than every 30 minutes, whenever the system next decides this app deserves a turn"* — which might be 30 minutes, might be 6 hours, might be never until the user opens the app.

## "Do this when the OS gives you space"

iOS apps that refresh feeds, sync mailboxes, or pre-download content have always worked this way. The classic `UIApplication.setMinimumBackgroundFetchInterval` API (now `BGAppRefreshTaskRequest`) never meant "run every N minutes" — it meant "I'd like a turn roughly this often; wake me when conditions are good." Backgrounder keeps that contract and makes it explicit across all platforms.

What "good conditions" means, per platform:

| Platform | Who decides the moment | Primary signal |
| --- | --- | --- |
| **iOS** | `BGTaskScheduler` | App-usage prediction — the system learns *when* you tend to open the app and wakes it shortly before. Plus battery, network, Low Power Mode. |
| **Android** | `WorkManager` → `JobScheduler` | Batches deferrable work into system maintenance windows; respects Doze and App Standby buckets. |
| **macOS** | `NSBackgroundActivityScheduler` | Picks an idle moment within the interval's tolerance window; biased by `qualityOfService`. |
| **JVM** | Library coroutines | In-process; fires on its own timer with no OS gatekeeper (see caveat below). |

The library does **not** add a "run opportunistically" flag — opportunistic *is* the default and the only mode for `WorkRequest.OneTime` / `WorkRequest.Periodic`. There is no "run exactly now on a wall clock" mode, because no mobile OS offers one for deferrable background work. If you need exact wall-clock execution, you want a user-visible alarm/notification (`UNUserNotificationCenter`, `AlarmManager.setExactAndAllowWhileIdle`), which is a different product surface and out of scope for this library.

## Nudging toward idle moments

You can bias dispatch toward genuinely-idle device states with [`WorkConstraints`](../recipes/network-required.md):

```kotlin
WorkRequest.Periodic(
    taskId = FeedSync.ID,
    interval = 1.hours,
    constraints = WorkConstraints(
        networkRequired = NetworkRequirement.Unmetered,  // Wi-Fi / Ethernet
        requiresCharging = true,                          // on power
        requiresDeviceIdle = true,                        // device not in active use
    ),
)
```

These are **hints the OS honours to varying degrees** — see the per-platform enforcement table in [`requiresDeviceIdle`](../recipes/network-required.md#require-the-device-to-be-idle). `requiresDeviceIdle` is real OS enforcement on Android (Doze / maintenance windows); on iOS, macOS, and JVM it's advisory — those systems already prefer idle moments themselves and expose no per-request idle knob. Adding constraints makes dispatch *less frequent and later*, never more frequent: every constraint is one more condition the system waits to satisfy.

## What can go wrong

These are the symptoms that send people to the issue tracker. Almost all of them are the opportunistic contract working as designed.

### "My periodic didn't run on time"

It was never going to run "on time." The interval is a floor. The system fired it when it decided your app deserved a turn — which, for an app the user rarely opens, can be many intervals late. **This is correct behaviour.** If you need the work to *catch up* after a long gap, compute the gap inside your worker from your own persisted `lastSyncedAt` — the scheduler fires a late cycle **once**, never N times back-to-back. See [missed cycles](../recipes/periodic.md).

### "It ran fine in development, then stopped on a real device"

App Standby buckets (Android) and usage-prediction (iOS) throttle apps the user has stopped opening. A test device you actively poke keeps the app in a "frequent" bucket; a real user's device demotes a rarely-opened app to "rare," and background dispatch slows to a trickle. Nothing is broken — the OS deprioritised an app the user isn't using.

### "Nothing fires at all on iOS"

Three usual causes, in order of likelihood:

1. **The user force-quit the app.** iOS then refuses *all* background dispatch until the user manually relaunches. This is unfixable by design — see [Force-quit caveat](../platforms/force-quit.md). Surface it in your UX.
2. **Low Power Mode** suspends background refresh entirely.
3. **Missing `Info.plist` entry** or `start()` not called before `didFinishLaunchingWithOptions` returns — see the [iOS launch sequence](../platforms/ios.md).

### "Timing differs between iOS and Android"

Expected. The two systems make independent decisions from different signals. Don't write code that assumes a shared cadence; write workers that are correct *whenever* they run, however far apart.

### "On JVM it fires exactly on schedule — why don't the others?"

The JVM target has **no OS gatekeeper**: it's library-owned coroutines firing on a `delay`-driven timer in your own process ([JVM platform notes](../platforms/jvm.md)). That makes it the odd one out — precise and eager, but only alive while your process is. Don't calibrate your expectations for the mobile targets against JVM behaviour; if anything, JVM is the "exact wall-clock" escape hatch the mobile platforms deliberately withhold.

## How to design for it

- **Make workers idempotent and gap-tolerant.** Assume any run may be the first in hours. Compute "what changed since last time" from your own persisted state, not from the assumption that the previous cycle ran on schedule.
- **Set user expectations in copy, not in code.** "Synced throughout the day," not "synced every 30 minutes."
- **Add constraints to save battery, not to control timing.** `requiresDeviceIdle` / `requiresCharging` / `Unmetered` make the OS *defer* to better conditions — they push dispatch later, which is usually what you want for non-urgent sync.
- **For "soon and small," use [`ExecutionHint.Expedited`](../recipes/retry-backoff.md).** It asks the OS for a sooner, shorter window (iOS App Refresh ~30 s; Android expedited quota). It is still opportunistic — just higher-priority within the system's budget.

## See also

- [Guarantees](guarantees.md) — the per-platform durability / timing / cancellation matrix.
- [Schedule a periodic](../recipes/periodic.md) — interval floors, missed-cycle coalescing, the iOS two-feed model.
- [Force-quit caveat (iOS)](../platforms/force-quit.md) — the one limitation the library genuinely cannot work around.
