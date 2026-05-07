# Getting started

Goal: a single working `BackgroundWorker` invocation from `commonMain`, dispatched by your platform's native scheduler.

There are four steps regardless of platform; the concrete code per step changes a bit between Android, iOS, and macOS. We'll do all three together — pick the tab that matches the platform you're building for first.

## 1. Add Backgrounder to your build

See [Installation](installation.md) for the version-catalog snippet and the platform-floor table. The short version is: add `dev.backgrounder:shared` to your `commonMain` dependencies, plus `dev.backgrounder:backgrounder-android` to `androidMain` if you target Android.

## 2. Define a `BackgroundWorker` in `commonMain`

Implement the single-method `BackgroundWorker` interface. Workers are *built by a factory at app launch* — not instantiated by reflection — so they receive their dependencies through their constructor.

```kotlin title="commonMain/SyncWorker.kt"
import dev.backgrounder.BackgroundWorker
import dev.backgrounder.TaskId
import dev.backgrounder.WorkResult
import dev.backgrounder.WorkerContext

class SyncWorker(
    private val repo: MyRepository,
) : BackgroundWorker {
    override suspend fun execute(context: WorkerContext): WorkResult {
        return try {
            repo.sync()
            WorkResult.Success
        } catch (t: Throwable) {
            // The library will retry per WorkRequest.backoff up to maxAttempts.
            WorkResult.Retry
        }
    }

    companion object {
        val ID = TaskId("dev.example.app.sync")
    }
}
```

## 3. Register the worker factory at app launch

This is the **DI seam**. The factory closes over your DI graph (Koin's `GlobalContext` is shown here), so a fresh `SyncWorker` is built per invocation with all its dependencies wired:

```kotlin
val registry = GlobalContext.get<WorkerRegistry>()
registry.register(SyncWorker.ID) { SyncWorker(get()) }   // get() resolves from Koin
```

You'll do this *after* `startKoin { ... }` and *before* `Backgrounder.registerHandlers()` (iOS / macOS) / `Backgrounder.markReady()` (Android).

## 4. Wire up the launch sequence

The library needs to hook into your platform's app-launch lifecycle. The three platforms differ in what each step does, but the *order* is always the same: ephemeral sweep → start DI → register factories → tell the library it can dispatch.

=== "Android"

    ```kotlin title="MyApp.kt — Application.onCreate"
    class MyApp : Application() {
        override fun onCreate() {
            super.onCreate()

            // 1. Sweep ephemeral jobs BEFORE Koin starts.
            Backgrounder.attachTo(this)

            // 2. Start Koin with workManagerFactory().
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

            // 4. Mark ready — clears the ephemeral-not-ready backstop.
            Backgrounder.markReady()
        }
    }
    ```

    Add to `AndroidManifest.xml` (disables WorkManager's auto-init so we control startup ordering):

    ```xml
    <provider
        android:name="androidx.work.impl.WorkManagerInitializer"
        android:authorities="${applicationId}.workmanager-init"
        tools:node="remove" />
    ```

=== "iOS"

    ```swift title="AppDelegate.swift"
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

            // 3. registerHandlers — sweeps ephemeral state, registers
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

=== "macOS"

    ```swift title="AppDelegate.swift"
    @main
    class AppDelegate: NSObject, NSApplicationDelegate {
        func applicationDidFinishLaunching(_ notification: Notification) {
            KoinKt.doInitKoin(/* + backgrounderMacOSModule */)
            let registry = KoinPlatformKt.getKoin().get(WorkerRegistry.self)
            registry.register(taskId: SyncWorker.companion.ID) {
                SyncWorker(repo: /* injected */)
            }
            BackgrounderRuntime.shared.registerHandlers()
        }
    }
    ```

    No `Info.plist` work needed; `NSBackgroundActivityScheduler` owns scheduling lifetime.

## 5. Schedule

From anywhere in your app:

```kotlin
import kotlin.time.Duration.Companion.seconds

val scheduler = GlobalContext.get<Scheduler>()

scheduler.schedule(
    WorkRequest.OneTime(
        taskId = SyncWorker.ID,
        constraints = WorkConstraints(networkRequired = NetworkRequirement.Any),
        backoff = BackoffPolicy.exponential(initialDelay = 30.seconds, maxAttempts = 5),
    ),
)
```

The platform scheduler will dispatch the worker when its constraints are satisfied. On Android it'll fire once the device is on a network. On iOS it'll fire when the system feels like it (after `earliestBeginDate`); see [Guarantees](concepts/guarantees.md) for what each platform actually promises.

## What's next

- **[Recipes](recipes/one-shot.md)** — task-oriented "how to do X" pages.
- **[Concepts → Worker context & DI](concepts/worker-context-and-di.md)** — the factory pattern in depth.
- **[Concepts → Ephemeral flag](concepts/ephemeral.md)** — defending against the "ran before init" Android foot-gun.
- **[Platforms → Force-quit caveat (iOS)](platforms/force-quit.md)** — read before shipping iOS.
