# Worker context & DI

## The factory pattern

Workers are *built by the user* — through a factory closure registered at app launch — and then *invoked by the library*. The factory closes over your DI graph (Koin's `GlobalContext`, in our example) so each invocation gets a fresh worker with its dependencies wired:

```kotlin
val registry = GlobalContext.get<WorkerRegistry>()

registry.register(SyncWorker.ID) { SyncWorker(get()) }      // singleton repo
registry.register(UploadWorker.ID) {                        // multi-arg
    UploadWorker(api = get(), retryPolicy = get())
}
```

The library calls your factory each time the platform dispatches a worker — never caches the worker instance. That makes statefulness inside the worker irrelevant: each invocation starts clean.

This is the `@HiltWorker` model from Android, generalised for KMP. The user owns DI; the library owns scheduling and dispatch. There is no overlap.

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
    val cancelsInFlight: Boolean,      // false on iOS — see Force-quit caveat
)
```

Read in your `execute` to checkpoint against the budget — for example, on iOS `Expedited` you have ~30 seconds total, so you'd save partial state every few seconds rather than after the whole job.
