# Test workers

## Unit-testing the worker itself

A `BackgroundWorker` is just a class with one `suspend fun`. Test it like any other suspending function:

```kotlin
import dev.backgrounder.PlatformCapabilities
import dev.backgrounder.WorkInput
import dev.backgrounder.WorkResult
import dev.backgrounder.WorkerContext
import dev.backgrounder.TaskId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class SyncWorkerTest {
    @Test
    fun successfulSyncReturnsSuccess() = runTest {
        val repo = FakeRepository(syncSucceeds = true)
        val worker = SyncWorker(repo)

        val ctx = WorkerContext(
            taskId = SyncWorker.ID,
            attempt = 0,
            input = WorkInput.empty(),
            capabilities = PlatformCapabilities(
                maxExecutionTime = 10.minutes,
                cancelsInFlight = true,
            ),
        )

        assertEquals(WorkResult.Success, worker.execute(ctx))
    }
}
```

`runTest` from `kotlinx-coroutines-test` runs the suspending body with virtual time, so anything the worker `delay`s skips instantly.

## Asserting against scheduling logic

For code that *schedules* work (rather than the worker body itself), you'll want to test against a `Scheduler` that records what was scheduled without actually invoking a platform scheduler.

In v1, the library ships an internal `FakeScheduler` for our own `commonTest` suite. A published `:testing` artifact with a stable, public `FakeScheduler` API is **planned for v2** so consumers can use the same fake from their `commonTest`.

Until v2 ships, the practical alternatives are:

1. **Wrap `Scheduler` in your own interface** — e.g. an `AppScheduler` your business logic depends on, with a fake implementation in test code. Three lines of indirection; lets you assert against a recorder.
2. **Use the v1 contract test as a reference** — the [`SchedulerContractTest`](https://github.com/paulbates/backgrounder/blob/main/shared/src/commonTest/kotlin/dev/backgrounder/SchedulerContractTest.kt) inside the library's own test source set is a worked example of the contract every `Scheduler` implementation honours; you can copy the pattern.

## Testing on Android with `WorkManagerTestInitHelper`

The `androidx.work:work-testing` library provides `WorkManagerTestInitHelper` for end-to-end testing on the Android JVM (Robolectric required). Backgrounder's own Android tests use it; see the [`WorkManagerSchedulerTest`](https://github.com/paulbates/backgrounder/blob/main/shared/src/androidHostTest/kotlin/dev/backgrounder/android/WorkManagerSchedulerTest.kt) (when it lands) for a worked example.

## Testing on iOS

`BGTaskScheduler` doesn't fire automatically in the iOS Simulator. Drive it from LLDB while paused at a breakpoint:

```
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"dev.example.app.sync"]
(lldb) e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateExpirationForTaskWithIdentifier:@"dev.example.app.sync"]
```

The simulate-launch hook is private (the leading underscore is intentional) and only available on Simulator builds. On device, you wait for the system to dispatch — which can take hours.
