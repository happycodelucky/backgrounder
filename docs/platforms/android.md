# Android launch sequence

The Android launch sequence is **three steps** — `create`, `register`, `start` — plus one mandatory wiring: tell `WorkManager` to use Backgrounder's `WorkerFactory` via `Configuration.Provider`.

```kotlin
import androidx.work.Configuration
import dev.backgrounder.Backgrounder
import dev.backgrounder.androidWorkerFactory
import dev.backgrounder.create

class MyApp : Application(), Configuration.Provider {
    lateinit var backgrounder: Backgrounder

    override fun onCreate() {
        super.onCreate()

        // 1. Construct. Eagerly sweeps any ephemeral work from prior runs.
        //    Does NOT trigger WorkManager.getInstance() — that's lazy until
        //    Configuration.Provider has had a chance to install our factory.
        backgrounder = Backgrounder.create(application = this)

        // 2. Register every BackgroundWorker factory. The closure is yours —
        //    resolve dependencies through whatever DI graph your app uses.
        backgrounder.register(SyncWorker.ID) { SyncWorker(repo = appGraph.repo) }
        backgrounder.register(UploadWorker.ID) {
            UploadWorker(api = appGraph.api, retryPolicy = appGraph.retryPolicy)
        }

        // 3. Start. Seals the registry; flips the ready gate so workers
        //    enqueued before this point may now dispatch.
        backgrounder.start()
    }

    // Tell WorkManager to use Backgrounder's WorkerFactory. Required.
    // Compose with Hilt's HiltWorkerFactory via DelegatingWorkerFactory if
    // you also use Hilt — see Concepts → Worker context & DI.
    override val workManagerConfiguration: Configuration get() =
        Configuration.Builder()
            .setWorkerFactory(backgrounder.androidWorkerFactory())
            .build()
}
```

## AndroidManifest

Disable WorkManager's default auto-init, which is mandatory whenever you implement `Configuration.Provider`:

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

Without this, WorkManager's auto-init content provider runs before `Application.onCreate` and locks the `Configuration` to its defaults — your `workManagerConfiguration` override would never be consulted.

## Construction order matters

`Configuration.Provider.workManagerConfiguration` is invoked the *first time anyone* calls `WorkManager.getInstance(context)`. The user must have constructed `backgrounder = Backgrounder.create(this)` before that happens — otherwise `androidWorkerFactory()` has nothing to return.

In practice this means: **construct `backgrounder` before `super.onCreate()` returns**. The snippet above puts it right after `super.onCreate()`, which is well within the window. Hilt has the same constraint for the same reason.

## What runs where

- `RegistryDispatchWorker` (the single Worker class registered with WorkManager) is dispatched on WorkManager's executor — effectively `Dispatchers.Default`.
- The worker's `execute()` runs on a coroutine that inherits that dispatcher; switch with `withContext(Dispatchers.IO)` for blocking IO.
- Logs from inside the worker are tagged `Backgrounder/<taskId>` (Kermit) and the thread is named `Backgrounder/<taskId>` for the duration of `execute()`. This is the mitigation for the single-bridge-worker design — every log includes the task id even though the Worker class is the same for every task.

## Multi-process apps

`Backgrounder.create(application)` must be called in `Application.onCreate` (which runs in *every* process — main and `:remote`), not from an `androidx.startup` initializer (which doesn't run in non-main processes). The factory closure pattern works the same in every process — each process holds its own `Backgrounder` instance, but they share the same `WorkManager` database, so scheduled work is consistent across processes.
