# Handle retries with backoff

Return `WorkResult.Retry` from your worker when a transient failure should retry per the request's `BackoffPolicy`. The library converts `Retry` to `Failure` automatically once `BackoffPolicy.maxAttempts` is exhausted.

```kotlin
override suspend fun execute(context: WorkerContext): WorkResult {
    return try {
        api.upload(payload)
        WorkResult.Success
    } catch (e: TransientHttpError) {
        log.w { "transient error on attempt ${context.attempt}; retrying" }
        WorkResult.Retry
    } catch (e: PermanentHttpError) {
        WorkResult.Failure("HTTP ${e.code}: ${e.message}")
    }
}
```

## Backoff policies

```kotlin
BackoffPolicy.linear(initialDelay = 30.seconds, maxAttempts = 5)
//   delay = initialDelay + initialDelay * attempt
//   30s → 60s → 90s → 120s → 150s

BackoffPolicy.exponential(initialDelay = 30.seconds, maxAttempts = 10)
//   delay = initialDelay * 2^attempt, capped at MAX_BACKOFF (5 hours)
//   30s → 60s → 120s → 240s → 480s ...
```

Floors and ceilings:

- `initialDelay` must be `>= 10 seconds` (matches WorkManager's `MIN_BACKOFF_MILLIS`).
- `maxAttempts` is in `1..30`. Default is 10.
- `MAX_BACKOFF` is 5 hours; exponential growth clamps there so iOS doesn't park `earliestBeginDate` a week into the future.

## How the cap is enforced

- **Android.** `RegistryDispatchWorker.doWork()` reads `runAttemptCount + 1` and compares against `BackoffPolicy.maxAttempts` (carried through `inputData`). When the cap is reached, the worker returns `Result.failure()` regardless of what the user code returned, so WorkManager doesn't reschedule.
- **iOS / macOS.** The library tracks attempts in its state store (`tasks.<id>.attempt`). On `Retry`, it increments and checks the cap; on cap-hit it stops resubmitting and treats the result as `Failure`.
- **Periodic.** For periodic workers, `maxAttempts` is a *per-cycle* cap that resets after each `Success`. Exhausting the cap mid-cycle resumes the regular cadence at the next interval rather than killing the schedule.

## Picking a policy

- Network ops: `exponential(30.seconds, maxAttempts = 8)` — most transient errors clear within minutes.
- IO ops on local resources: `linear(10.seconds, maxAttempts = 3)` — failure is more often structural than transient.
- Idempotent batch processing: `exponential(60.seconds, maxAttempts = 5)` — burst long enough to clear, but don't grind on a permanent fault.
