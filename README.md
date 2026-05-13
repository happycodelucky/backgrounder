# Backgrounder

A Kotlin Multiplatform library that wraps platform background-scheduling primitives behind one API:

- **Android**: Jetpack `WorkManager` (one-shot + periodic, with constraints, retry, expedited).
- **iOS 18+**: `BGTaskScheduler` (one-shot + library-emulated periodic; force-quit caveat documented).
- **macOS 15+**: Foundation's `NSBackgroundActivityScheduler` (one-shot + native periodic).

Documentations can be found [here](https://happycodelucky.github.io/backgrounder/)

---

## What you write

```kotlin
// commonMain
class SyncWorker(private val repo: MyRepository) : BackgroundWorker {
    override suspend fun execute(context: WorkerContext): WorkResult {
        return try {
            repo.sync()
            WorkResult.Success
        } catch (t: Throwable) {
            WorkResult.Retry
        }
    }

    companion object {
        val ID = TaskId("dev.example.app.sync")
    }
}
```

The library never instantiates your worker by reflection — you give it a factory at app launch:

```kotlin
backgrounder.register(SyncWorker.ID) { SyncWorker(repo = appGraph.repo) }
```

The closure is yours: resolve dependencies through Koin, Hilt, kotlin-inject, hand-wired singletons — whatever your app already uses. A fresh `SyncWorker` is built per invocation with all its dependencies wired.

Then schedule from anywhere:

```kotlin
backgrounder.scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        constraints = WorkConstraints(networkRequired = NetworkRequirement.Any),
        backoff = BackoffPolicy.exponential(initialDelay = 30.seconds, maxAttempts = 5),
    ),
)
```

`networkRequired` is honoured everywhere — Android holds the worker via WorkManager's native constraint gating; iOS and macOS use a library-managed pre-execution reachability gate (powered by [reachable](https://github.com/happycodelucky/reachable)) that waits up to 5 seconds before short-circuiting to `WorkResult.Retry`. See [Recipes → Require a network connection](https://happycodelucky.github.io/backgrounder/recipes/network-required/).

Or for "do this work in the background **right now** and give me back the typed result" — no constraints, no retries, structured `await` — use `runNow`:

```kotlin
val saved: SavedDocument = backgrounder.runNow(saveTaskId) {
    repo.save(draft)
}
```

`runNow` runs on the platform's real background primitive so the work survives if the user backgrounds the app mid-call — `UIApplication.beginBackgroundTask` on iOS, `WorkManager` on Android, a library scope on macOS. See the [Run now recipe](https://happycodelucky.github.io/backgrounder/recipes/run-now/) for the full contract.

---

## Launch sequence — Android

`Application.onCreate` does **three** things — *create*, *register*, *start* — plus one mandatory wiring: install Backgrounder's `WorkerFactory` via `Configuration.Provider`.

```kotlin
import androidx.work.Configuration
import com.happycodelucky.backgrounder.Backgrounder
import com.happycodelucky.backgrounder.androidWorkerFactory
import com.happycodelucky.backgrounder.create

class MyApp : Application(), Configuration.Provider {
    lateinit var backgrounder: Backgrounder

    override fun onCreate() {
        super.onCreate()

        // 1. Construct. Eagerly sweeps ephemeral work from prior runs.
        backgrounder = Backgrounder.create(application = this)

        // 2. Register every BackgroundWorker factory.
        backgrounder.register(SyncWorker.ID) { SyncWorker(repo = appGraph.repo) }

        // 3. Start. Seals the registry; flips the ready gate.
        backgrounder.start()
    }

    // Tell WorkManager to use Backgrounder's WorkerFactory. Required.
    override val workManagerConfiguration: Configuration get() =
        Configuration.Builder()
            .setWorkerFactory(backgrounder.androidWorkerFactory())
            .build()
}
```

Add to your app's `AndroidManifest.xml` to disable WorkManager's default auto-init (mandatory whenever you implement `Configuration.Provider`):

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

---

## Launch sequence — iOS

```swift
@main
final class AppDelegate: NSObject, UIApplicationDelegate {
    // Pick a tick identifier in your app's reverse-DNS namespace. The library
    // uses it as the BGAppRefreshTaskRequest that wakes periodic dispatch in
    // the background. Periodic task ids do not need their own Info.plist
    // entries — the tick handles them.
    let backgrounder = Backgrounder.companion.create(
        tickIdentifier: "dev.example.app.background-tick"
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?,
    ) -> Bool {
        backgrounder.register(taskId: SyncWorker.companion.ID) {
            SyncWorker(repo: AppGraph.shared.repository)
        }
        backgrounder.start()
        return true
    }
}
```

Add the tick identifier (mandatory) plus one entry per `WorkRequest.OneTime` task id you schedule to your app's `Info.plist`. Periodic ids do **not** need their own entries.

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.background-tick</string>  <!-- mandatory: matches tickIdentifier above -->
    <string>dev.example.app.upload</string>           <!-- one-shot WorkRequest.OneTime -->
</array>
```

A missing tick identifier is reported with a Kermit error during `backgrounder.start()` (close to the cause; not at first `schedule()`). Missing one-shot ids surface as warnings — the library can't tell at registration time which ids will be used as one-shots vs periodics.

See [docs/platforms/ios.md](docs/platforms/ios.md) for how the foreground/background dispatcher works, the coalescing contract, and the per-path execution windows.

### iOS testing

Background tasks don't fire automatically in the iOS Simulator. Drive them from LLDB while paused:

```
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"dev.example.app.background-tick"]
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateExpirationForTaskWithIdentifier:@"dev.example.app.background-tick"]
```

Use the tick identifier (not the per-task id) to simulate background dispatch of periodics. For one-shots, use the per-task id you scheduled. The foreground dispatch loop runs normally regardless and doesn't need LLDB.

---

## Launch sequence — macOS

```swift
@main
final class AppDelegate: NSObject, NSApplicationDelegate {
    let backgrounder = Backgrounder.companion.create()

    func applicationDidFinishLaunching(_ notification: Notification) {
        backgrounder.register(taskId: SyncWorker.companion.ID) {
            SyncWorker(repo: AppGraph.shared.repository)
        }
        backgrounder.start()
    }

    func applicationWillTerminate(_ notification: Notification) {
        backgrounder.shutdown()
    }
}
```

`NSBackgroundActivityScheduler` owns scheduling lifetime, so there's no force-quit caveat — periodic schedules survive cleanly.

---

## What each platform actually guarantees

Read at runtime via `backgrounder.scheduler.guarantees()`:

|                              | Android `WorkManager` | iOS 18 `BGTaskScheduler` | macOS 15 `NSBackgroundActivityScheduler` |
| ---------------------------- | --------------------- | ------------------------ | ---------------------------------------- |
| `survivesProcessDeath`       | true                  | true                     | true                                     |
| `survivesReboot`             | true                  | true                     | true                                     |
| `survivesForceQuit`          | **true**              | **false**                | true                                     |
| `honoursWallClock`           | approx                | **false** (hint only)    | approx                                   |
| `supportsRetryBackoff`       | true                  | true (emulated)          | true (emulated)                          |
| `cancelsInFlight`            | **true**              | **false**                | true                                     |
| `minimumPeriodicInterval`    | 15 min                | 15 min recommended       | 1 sec                                    |
| `maxConcurrentTasks`         | unbounded-ish         | ~1000                    | unbounded-ish                            |

iOS-specific: when the user **force-quits the app from the App Switcher**, all background tasks stop firing until the user launches the app again. That's Apple's design — we can't paper over it. Surface this in your UX (e.g. "Open the app daily so we can sync.").

---

## The `ephemeral` flag

`WorkRequest(ephemeral = true)` declares "this work must be re-scheduled by app code after init; do not run it from a state I didn't deliberately put it in." On every cold app start, the library cancels every ephemeral job *before* any worker can dispatch.

Use it when the worker depends on app state initialised after `Application.onCreate` / `application(_:didFinishLaunchingWithOptions:)`. The sweep happens at:
- **Android**: inside `Backgrounder.create(application)`, before any worker can dispatch.
- **iOS / macOS**: top of `backgrounder.start()`.

On Android, the sweep is augmented by a per-instance ready gate: if WorkManager somehow fires an ephemeral worker before `backgrounder.start()` has been called, the worker returns `Failure("dispatched before ephemeralReady")` immediately rather than running with stale state.

---

## Build & test

[`mise`](https://mise.jdx.dev) pins the JDK, Gradle bootstrap, Python (mkdocs), and `gh` — see [`mise.toml`](./mise.toml). One-time bootstrap:

```bash
brew install mise
mise trust && mise install
```

Common tasks:

```bash
mise run check          # all unit tests across iOS sim, macOS native, Android JVM
mise run build:ios      # iOS device + Apple Silicon simulator debug frameworks, SKIE-enhanced
mise run xcframework    # release Backgrounder.xcframework (KMMBridge artifact)

# Raw Gradle equivalents, for reference:
./gradlew :backgrounder:check
./gradlew :backgrounder:linkDebugFrameworkIosArm64
./gradlew :backgrounder:assembleBackgrounderXCFramework
```

`mise run check` runs:
- `iosSimulatorArm64Test` — kotlin-test + Turbine + multiplatform-settings test impl
- `macosArm64Test` — same suite, native macOS
- `testAndroidHostTest` — JVM-side tests via Robolectric-free pure mappers

---

## Repository conventions

- **Versions** (`gradle/libs.versions.toml`) are the single source of truth. Web-search before bumping any dependency (CLAUDE.md §2). Kotlin is pinned at the highest version SKIE supports — currently 2.3.20 with SKIE 0.10.11.
- Every `suspend fun` reachable from Swift carries `@Throws(CancellationException::class)`; every public method carries `@ObjCName(swiftName = ...)` so the call site reads like Swift (CLAUDE.md §8).
- `internal` by default; widen visibility only when needed (CLAUDE.md §3).
- **DI is a user choice.** The library uses constructor injection internally and a factory-closure seam for user code; no DI container is required.

See [`CLAUDE.md`](./CLAUDE.md) for the full project conventions.
