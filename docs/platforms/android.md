# Android launch sequence

`Application.onCreate` does **four** things, in this order:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Sweep ephemeral jobs BEFORE Koin starts. Defends against
        //    JobScheduler-fires-during-init for jobs marked
        //    `WorkRequest(ephemeral = true)`. See Concepts → Ephemeral.
        Backgrounder.attachTo(this)

        // 2. Start Koin with workManagerFactory(). koin-androidx-workmanager
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
        registry.register(UploadWorker.ID) { UploadWorker(get(), get()) }

        // 4. Mark ready — clears the ephemeral-not-ready backstop in
        //    RegistryDispatchWorker.
        Backgrounder.markReady()
    }
}
```

## AndroidManifest

Disable WorkManager's auto-init so we control the startup ordering:

```xml
<provider
    android:name="androidx.work.impl.WorkManagerInitializer"
    android:authorities="${applicationId}.workmanager-init"
    tools:node="remove" />
```

Without this, WorkManager's content provider runs before `Application.onCreate`, which means `Backgrounder.attachTo` can't sweep ephemeral work before WorkManager starts dispatching.

## What runs where

- `RegistryDispatchWorker` (the single Worker class registered with WorkManager) is dispatched on WorkManager's executor — effectively `Dispatchers.Default`.
- The worker's `execute()` runs on a coroutine that inherits that dispatcher; switch with `withContext(Dispatchers.IO)` for blocking IO.
- Logs from inside the worker are tagged `Backgrounder/<taskId>` (Kermit) and the thread is named `Backgrounder/<taskId>` for the duration of `execute()`. This is the mitigation for the single-bridge-worker design — every log includes the task id even though the Worker class is the same for every task.

## Multi-process apps

If your app runs WorkManager in a `:remote` process, Koin must be started in `Application.onCreate` (which runs in *every* process), **not** via `androidx.startup` (which doesn't run in non-main processes). The library already enforces this; just don't move Koin init into a `Startup.Initializer` and you're fine.
