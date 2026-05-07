package dev.backgrounder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable

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

    /** Linear: `delay = initialDelay + initialDelay * attempt`. */
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

    /** Exponential: `delay = initialDelay * 2^attempt`, capped at [MAX_BACKOFF]. */
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
        // WorkManager's MIN_BACKOFF_MILLIS == 10s.
        public val MIN_BACKOFF: Duration = 10.seconds

        // WorkManager's MAX_BACKOFF_MILLIS == 5h, but in practice we cap lower so iOS
        // emulation doesn't park earliestBeginDate in next week.
        public val MAX_BACKOFF: Duration = (5 * 60).seconds * 60 // 5 hours

        public const val DEFAULT_MAX_ATTEMPTS: Int = 10
        public const val MAX_MAX_ATTEMPTS: Int = 30
        private const val MAX_SHIFT_BITS: Int = 30

        public fun linear(
            initialDelay: Duration = 30.seconds,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        ): BackoffPolicy = Linear(initialDelay, maxAttempts)

        public fun exponential(
            initialDelay: Duration = 30.seconds,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        ): BackoffPolicy = Exponential(initialDelay, maxAttempts)
    }
}
