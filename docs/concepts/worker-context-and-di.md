# Worker context & DI

## The factory pattern

Workers are *built by the user* — through a factory closure registered at app launch — and then *invoked by the library*. The closure has access to whatever DI graph (or hand-wired singletons) you've already assembled, so each dispatch gets a fresh worker with its dependencies wired:

```kotlin
backgrounder.register(SyncWorker.ID) { SyncWorker(repo = appGraph.repository) }
backgrounder.register(UploadWorker.ID) {
    UploadWorker(api = appGraph.api, retryPolicy = appGraph.retryPolicy)
}
```

The library calls your factory each time the platform dispatches a worker — never caches the worker instance. That makes statefulness inside the worker irrelevant: each invocation starts clean.

This is the `@HiltWorker` model from Android, generalised for KMP. **The user owns DI; the library owns scheduling and dispatch. There is no overlap.**

## DI is a user choice

Backgrounder doesn't ship a DI module and doesn't require any DI framework. The factory closure is just a `() -> BackgroundWorker` — what's *inside* it is whatever you want.

=== "No DI (hand-wired)"

    ```kotlin
    object AppGraph {
        val repository = Repository(api = Api(...))
    }

    backgrounder.register(SyncWorker.ID) {
        SyncWorker(repo = AppGraph.repository)
    }
    ```

=== "Koin"

    ```kotlin
    backgrounder.register(SyncWorker.ID) {
        SyncWorker(repo = getKoin().get())   // resolve from your Koin graph
    }
    ```

=== "Hilt (Android)"

    ```kotlin
    @AndroidEntryPoint
    class MyApp : Application(), Configuration.Provider {
        @Inject lateinit var repository: Repository
        @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
        lateinit var backgrounder: Backgrounder

        override fun onCreate() {
            super.onCreate()
            backgrounder = Backgrounder.create(application = this)
            backgrounder.register(SyncWorker.ID) { SyncWorker(repository) }
            backgrounder.start()
        }

        // Chain Hilt's WorkerFactory with Backgrounder's via DelegatingWorkerFactory.
        override val workManagerConfiguration: Configuration get() =
            Configuration.Builder()
                .setWorkerFactory(DelegatingWorkerFactory().apply {
                    addFactory(hiltWorkerFactory)
                    addFactory(backgrounder.androidWorkerFactory())
                })
                .build()
    }
    ```

=== "kotlin-inject"

    ```kotlin
    backgrounder.register(SyncWorker.ID) {
        SyncWorker(repo = appComponent.repository)
    }
    ```

The closure is yours. Use any DI you want — or none.

## `WorkerContext` — what the runtime hands you

Per-invocation runtime data:

```kotlin
class WorkerContext internal constructor(
    val taskId: TaskId,
    val attempt: Int,                    // 0-based
    val input: WorkInput,
    val capabilities: PlatformCapabilities,
)
```

There is **no** `coroutineScope` field. Cancellation flows through normal coroutine cancellation — `BackgroundWorker.execute` is a `suspend fun`, so `currentCoroutineContext().job` is the worker's job. Use `coroutineScope { ... }` or `supervisorScope { ... }` inside `execute` if you want sibling work.

## `PlatformCapabilities` — what the OS gave you

```kotlin
data class PlatformCapabilities(
    val maxExecutionTime: Duration,    // ~30s on iOS Expedited; ~10min on Android Standard.
    val cancelsInFlight: Boolean,      // false on iOS — see Cancel work recipe and Guarantees
)
```

Read in your `execute` to checkpoint against the budget — for example, on iOS `Expedited` you have ~30 seconds total, so you'd save partial state every few seconds rather than after the whole job.
