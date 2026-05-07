# Backgrounder

A Kotlin Multiplatform library that wraps platform background-scheduling primitives behind one API:

- **Android**: Jetpack `WorkManager` (one-shot + periodic, with constraints, retry, expedited).
- **iOS 18+**: `BGTaskScheduler` (one-shot + library-emulated periodic; force-quit caveat documented).
- **macOS 15+**: Foundation's `NSBackgroundActivityScheduler` (one-shot + native periodic).

UI is out of scope (CLAUDE.md §1) — Backgrounder is the *headless* `:shared` KMP module. Each platform app consumes it natively.

> v1 deliberately ships a small, correct surface. Reactive `observe()`, an `ExecutionHint.LongRunning` for Android foreground-service work, fine-grained Android-only constraints, and a published `:testing` artifact are all v2 work — see the plan at `~/.claude/plans/on-android-there-is-vectorized-dawn.md`.

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
val registry = GlobalContext.get<WorkerRegistry>()
registry.register(SyncWorker.ID) { SyncWorker(get()) } // `get()` from Koin
```

The factory closes over your DI graph, so a fresh `SyncWorker` is built per invocation with all its dependencies wired.

Then schedule from anywhere:

```kotlin
val scheduler = GlobalContext.get<Scheduler>()
scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        constraints = WorkConstraints(networkRequired = NetworkRequirement.Any),
        backoff = BackoffPolicy.exponential(initialDelay = 30.seconds, maxAttempts = 5),
    ),
)
```

---

## Launch sequence — Android

`Application.onCreate` does **four** things, in this order:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Sweep ephemeral jobs BEFORE Koin starts. Defends against the
        //    JobScheduler-fires-during-init race for jobs marked
        //    `WorkRequest(ephemeral = true)`.
        Backgrounder.attachTo(this)

        // 2. Start Koin with workManagerFactory() — koin-androidx-workmanager
        //    wires RegistryDispatchWorker through Koin's WorkerFactory.
        startKoin {
            androidContext(this@MyApp)
            workManagerFactory()
            modules(
                backgrounderCommonModule,
                backgrounderAndroidModule,
                /* your modules */,
            )
        }

        // 3. Register every BackgroundWorker factory.
        val registry = GlobalContext.get<WorkerRegistry>()
        registry.register(SyncWorker.ID) { SyncWorker(get()) }
        // ...

        // 4. Mark ready — clears the ephemeral-not-ready backstop in
        //    RegistryDispatchWorker.
        Backgrounder.markReady()
    }
}
```

Add to your app's `AndroidManifest.xml` (CLAUDE.md §10 — disables WorkManager's auto-init so we control startup ordering):

```xml
<provider
    android:name="androidx.work.impl.WorkManagerInitializer"
    android:authorities="${applicationId}.workmanager-init"
    tools:node="remove" />
```

---

## Launch sequence — iOS

`AppDelegate.application(_:didFinishLaunchingWithOptions:)` does **three** things:

```swift
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?,
    ) -> Bool {
        // 1. Start Koin.
        KoinKt.doInitKoin(/* your platform module + backgrounderCommonModule + backgrounderIosModule */)

        // 2. Register every worker factory (use the Kotlin lambda directly via SKIE).
        let registry = KoinPlatformKt.getKoin().get(WorkerRegistry.self)
        registry.register(taskId: SyncWorker.companion.ID) { SyncWorker(repo: ...) }

        // 3. Register OS handlers — sweeps ephemeral state, registers
        //    BGTaskScheduler launch handlers, and resurrects active periodics.
        BackgrounderRuntime.shared.registerHandlers()
        return true
    }
}
```

Add every Backgrounder task id to your app's `Info.plist`:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.sync</string>
    <!-- ...one per TaskId you register -->
</array>
```

Missing identifiers are reported with a Kermit error during `registerHandlers()` (close to the cause; not at first `schedule()`).

### iOS testing

Background tasks don't fire automatically in the iOS Simulator. Drive them from LLDB while paused:

```
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"dev.example.app.sync"]
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateExpirationForTaskWithIdentifier:@"dev.example.app.sync"]
```

---

## Launch sequence — macOS

```swift
@main
class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        KoinKt.doInitKoin(/* + backgrounderMacosModule */)
        let registry = KoinPlatformKt.getKoin().get(WorkerRegistry.self)
        registry.register(taskId: SyncWorker.companion.ID) { SyncWorker(repo: ...) }
        BackgrounderRuntime.shared.registerHandlers()
    }
}
```

`NSBackgroundActivityScheduler` owns scheduling lifetime, so there's no force-quit caveat — periodic schedules survive cleanly.

---

## What each platform actually guarantees

Read at runtime via `Scheduler.guarantees()`:

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
- **Android**: top of `Backgrounder.attachTo`, before Koin starts.
- **iOS / macOS**: top of `Backgrounder.registerHandlers`.

On Android, the sweep is augmented by an `ephemeralReady` backstop: if WorkManager somehow fires an ephemeral worker before `Backgrounder.markReady()` has been called, the worker returns `Failure("dispatched before ephemeralReady")` immediately rather than running with stale state.

---

## Build & test

```bash
./gradlew check                                      # all unit tests across iOS sim, macOS native, Android JVM
./gradlew :shared:linkDebugFrameworkIosArm64         # iOS device framework, SKIE-enhanced
./gradlew :shared:assembleBackgrounderXCFramework    # the KMMBridge-consumable artifact
```

`./gradlew check` runs:
- `iosSimulatorArm64Test` — kotlin-test + Turbine + multiplatform-settings test impl
- `macosArm64Test` — same suite, native macOS
- `testAndroidHostTest` — JVM-side tests via Robolectric-free pure mappers + AtomicBoolean checks

Test counts as of the v1 milestone: 39 commonTest + 8 androidHostTest = 47 tests.

---

## Repository conventions

- **Versions** (`gradle/libs.versions.toml`) are the single source of truth. Web-search before bumping any dependency (CLAUDE.md §2). Kotlin is pinned at the highest version SKIE supports — currently 2.3.20 with SKIE 0.10.11.
- Every `suspend fun` reachable from Swift carries `@Throws(CancellationException::class)`; every public method carries `@ObjCName(swiftName = ...)` so the call site reads like Swift (CLAUDE.md §8).
- `internal` by default; widen visibility only when needed (CLAUDE.md §3).

See [`CLAUDE.md`](./CLAUDE.md) for the full project conventions.
