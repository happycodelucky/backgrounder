package com.happycodelucky.backgrounder

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A unit of background work the user implements.
 *
 * Workers are *instantiated by the library* through a factory the user
 * registers in [WorkerRegistry]. Inject your dependencies into the factory's
 * closure (typically by resolving from Koin); never look up dependencies
 * inside `execute()`.
 *
 * `execute()` runs on a coroutine dispatcher chosen by the platform actual:
 * - Android: WorkManager's executor (effectively `Dispatchers.Default`).
 * - iOS / macOS: a `SupervisorJob`-rooted scope on `Dispatchers.Default`.
 *
 * Always a `suspend fun`. **No `@Throws(CancellationException::class)`** —
 * this repo uses SKIE, which bridges `suspend fun` as Swift `async throws`
 * and routes coroutine cancellation through Swift's native `Task.cancel` /
 * `CancellationError` (CLAUDE.md §8). Domain exceptions thrown by the user's
 * `execute()` body are caught at the platform actual and converted into
 * `WorkResult.Retry` / `WorkResult.Failure` — they don't cross the iOS-actual
 * boundary uncatchable.
 *
 * `@OptIn(ExperimentalObjCName::class)`: Swift-rename annotation for boundary
 * refinement. Stable in practice and required by SKIE.
 */
@OptIn(ExperimentalObjCName::class)
public fun interface BackgroundWorker {
    @ObjCName(swiftName = "execute")
    public suspend fun execute(context: WorkerContext): WorkResult
}
