package dev.backgrounder

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How a [WorkRequest] is retried after [WorkResult.Retry].
 *
 * Both shapes carry [maxAttempts]: once a worker has been attempted that many
 * times within a single retry cycle, the library converts the next [WorkResult.Retry]
 * to [WorkResult.Failure] (Android: returns `Result.failure()`; iOS: stops
 * resubmitting). For periodic workers, the counter resets after each
 * [WorkResult.Success] cycle.
 */
@Serializable
public sealed interface BackoffPolicy {
    public val maxAttempts: Int

    public fun delayFor(attempt: Int): Duration

    /**
     * Linear: `delay = initialDelay × (attempt + 1)`. So `delayFor(0) = initialDelay`,
     * `delayFor(1) = 2 × initialDelay`, `delayFor(2) = 3 × initialDelay`, …
     *
     * `attempt` is 0-indexed, matching Android `WorkManager`'s `runAttemptCount`
     * (the count of *prior* failed runs).
     */
    @Serializable
    public data class Linear(
        val initialDelay: Duration = 30.seconds,
        override val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    ) : BackoffPolicy {
        init {
            require(initialDelay >= MIN_BACKOFF) { "initialDelay must be >= $MIN_BACKOFF" }
            require(maxAttempts in 1..MAX_MAX_ATTEMPTS) { "maxAttempts must be 1..$MAX_MAX_ATTEMPTS" }
        }

        override fun delayFor(attempt: Int): Duration = initialDelay * (attempt + 1)
    }

    /**
     * Exponential: `delay = initialDelay × 2^attempt`, capped at [MAX_BACKOFF].
     * `delayFor(0) = initialDelay`, `delayFor(1) = 2 × initialDelay`,
     * `delayFor(2) = 4 × initialDelay`, …
     *
     * `attempt` is 0-indexed (see [Linear]).
     */
    @Serializable
    public data class Exponential(
        val initialDelay: Duration = 30.seconds,
        override val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    ) : BackoffPolicy {
        init {
            require(initialDelay >= MIN_BACKOFF) { "initialDelay must be >= $MIN_BACKOFF" }
            require(maxAttempts in 1..MAX_MAX_ATTEMPTS) { "maxAttempts must be 1..$MAX_MAX_ATTEMPTS" }
        }

        override fun delayFor(attempt: Int): Duration {
            // Cap the shift: 1 shl 30 = ~1 billion, multiplied by 30s is well within
            // Duration's representable range. The MAX_BACKOFF clamp engages much earlier.
            val multiplier = 1 shl attempt.coerceIn(0, MAX_SHIFT_BITS)
            val raw = initialDelay * multiplier
            return if (raw < MAX_BACKOFF) raw else MAX_BACKOFF
        }
    }

    public companion object {
        /**
         * Minimum allowed [initialDelay] — matches Android WorkManager's `MIN_BACKOFF_MILLIS`
         * (10 seconds) to keep cross-platform behavior identical.
         */
        public val MIN_BACKOFF: Duration = 10.seconds

        /**
         * Maximum delay produced by [Exponential.delayFor]. Capped at 5 hours so
         * iOS emulation does not park `earliestBeginDate` beyond a practical horizon.
         */
        public val MAX_BACKOFF: Duration = (5 * 60).seconds * 60 // 5 hours

        /** Default value for [maxAttempts] when not specified. */
        public const val DEFAULT_MAX_ATTEMPTS: Int = 10

        /** Upper bound for [maxAttempts]. */
        public const val MAX_MAX_ATTEMPTS: Int = 30
        private const val MAX_SHIFT_BITS: Int = 30

        /** Convenience factory for [Linear] with the given parameters. */
        public fun linear(
            initialDelay: Duration = 30.seconds,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        ): BackoffPolicy = Linear(initialDelay, maxAttempts)

        /** Convenience factory for [Exponential] with the given parameters. */
        public fun exponential(
            initialDelay: Duration = 30.seconds,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        ): BackoffPolicy = Exponential(initialDelay, maxAttempts)
    }
}
