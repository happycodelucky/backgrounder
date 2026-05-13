package com.happycodelucky.backgrounder

/**
 * The outcome a [BackgroundWorker] returns from `execute()`.
 *
 * Sealed and named — not Kotlin's [Result] type — so SKIE generates a real
 * exhaustive Swift `enum` (CLAUDE.md §8 rule 4).
 */
public sealed interface WorkResult {
    /** Work completed; do not retry. */
    public data object Success : WorkResult

    /**
     * Work failed permanently; do not retry. The [reason] is a short
     * human-readable diagnostic — *not* an exception payload.
     */
    public data class Failure(
        val reason: String,
    ) : WorkResult

    /**
     * Transient failure; retry per the request's [BackoffPolicy]. The library
     * converts this to [Failure] once `BackoffPolicy.maxAttempts` is exhausted.
     */
    public data object Retry : WorkResult
}
