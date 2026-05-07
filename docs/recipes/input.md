# Pass input to a worker

`WorkInput` is a typed key/value bag, capped at **10 240 bytes** when serialised — the same hard cap as Android's `androidx.work.Data`. The cap is enforced cross-platform so you can't accidentally write input that survives Android-only-or-iOS-only.

```kotlin
val input = WorkInput.of(
    "imageUri" to WorkValue.StringValue("file:///path/to/image.jpg"),
    "quality" to WorkValue.LongValue(85L),
    "thumbnail" to WorkValue.BooleanValue(true),
)

scheduler.schedule(
    WorkRequest.OneTime(
        taskId = ProcessImageWorker.ID,
        input = input,
    ),
)
```

Inside the worker:

```kotlin
override suspend fun execute(context: WorkerContext): WorkResult {
    val uri = (context.input["imageUri"] as? WorkValue.StringValue)?.value
        ?: return WorkResult.Failure("missing imageUri")
    val quality = (context.input["quality"] as? WorkValue.LongValue)?.value ?: 75L
    // ...
}
```

## Supported value types

`WorkValue` is sealed:

- `StringValue(String)`
- `LongValue(Long)`
- `DoubleValue(Double)`
- `BooleanValue(Boolean)`
- `BytesValue(ByteArray)`

This is the intersection of `androidx.work.Data`'s primitive set and what serialises cleanly to JSON for iOS / macOS persistence. `Int` is intentionally absent — use `Long`. Nested objects are intentionally absent — flatten or serialise to a string.

## Why JSON for the wire format

On Android, the *whole* `WorkInput` is JSON-encoded into a single `Data` string key (rather than projecting each `WorkValue` onto a `Data` primitive). Reasons:

1. `Data`'s primitive set is narrower than `WorkValue`'s on some Android versions.
2. The size cap is enforced at `WorkInput.of()` time using the same serializer, so we don't risk "fits one shape, doesn't fit the other."
3. iOS / macOS persist input the same way (in the library state store), so the wire format is uniform across platforms.

## Sizing

```kotlin
WorkInput.of("payload" to WorkValue.StringValue("x".repeat(20_000)))
// → throws IllegalArgumentException: WorkInput exceeds the 10240-byte cap
```

If you legitimately need to pass more than 10 KB to a worker, store the payload yourself (your DB, a temp file) and pass an opaque key in the input.
