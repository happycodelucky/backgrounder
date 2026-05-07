package dev.backgrounder

import kotlin.coroutines.cancellation.CancellationException

/**
 * A unit of background work the user implements.
 *
 * Workers are *instantiated by the library* through a factory the user
 * registers in [WorkerRegistry]. Inject your dependencies into the factory's
 * closure (typically by resolving from Koin's `GlobalContext`); never look up
 * dependencies inside `execute()`.
 *
 * `execute()` runs on a coroutine dispatcher chosen by the platform actual:
 * - Android: WorkManager's executor (effectively `Dispatchers.Default`).
 * - iOS / macOS: a `SupervisorJob`-rooted scope on `Dispatchers.Default`.
 *
 * Always a `suspend fun`. CLAUDE.md §8 requires `@Throws(CancellationException::class)`
 * on every `suspend fun` crossing the Swift boundary so iOS callers see an
 * `async throws` rather than an unrecoverable crash on cancellation.
 */
public fun interface BackgroundWorker {
    @Throws(CancellationException::class)
    public suspend fun execute(context: WorkerContext): WorkResult
}
