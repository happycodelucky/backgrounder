package dev.backgrounder

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A request to schedule background work, identified by a stable [TaskId].
 *
 * Sealed: v1 supports [OneTime] and [Periodic]. Both share an [ephemeral] flag
 * for the cold-launch sweep — see `Backgrounder.attachTo` /
 * `Backgrounder.registerHandlers`.
 */
@Serializable
public sealed interface WorkRequest {
    public val taskId: TaskId
    public val constraints: WorkConstraints
    public val input: WorkInput

    /**
     * If `true`, this request is cleared at app cold-start before any worker
     * dispatches. Schedule it again from app code at a known-safe init point.
     *
     * Use this when the worker depends on app state that is initialized after
     * `Application.onCreate` / `application(_:didFinishLaunchingWithOptions:)`.
     */
    public val ephemeral: Boolean

    /** Run once; survives process death and reboot unless [ephemeral]. */
    @Serializable
    public data class OneTime(
        override val taskId: TaskId,
        override val constraints: WorkConstraints = WorkConstraints(),
        override val input: WorkInput = WorkInput.empty(),
        override val ephemeral: Boolean = false,
        val initialDelay: Duration = Duration.ZERO,
        val backoff: BackoffPolicy = BackoffPolicy.exponential(30.seconds),
        val executionHint: ExecutionHint = ExecutionHint.Standard,
    ) : WorkRequest {
        init {
            require(initialDelay >= Duration.ZERO) { "initialDelay must be >= 0" }
        }
    }

    /**
     * Repeats indefinitely until cancelled. Android floor is 15 minutes
     * (validated by the scheduler at enqueue time); iOS / macOS have no system
     * floor but we recommend ≥ 15 minutes for parity.
     */
    @Serializable
    public data class Periodic(
        override val taskId: TaskId,
        override val constraints: WorkConstraints = WorkConstraints(),
        override val input: WorkInput = WorkInput.empty(),
        override val ephemeral: Boolean = false,
        val interval: Duration,
        val flexWindow: Duration? = null,
    ) : WorkRequest {
        init {
            require(interval >= MIN_RECOMMENDED_INTERVAL) {
                "interval must be >= $MIN_RECOMMENDED_INTERVAL (Android floor); was $interval"
            }
            if (flexWindow != null) {
                require(flexWindow > Duration.ZERO && flexWindow < interval) {
                    "flexWindow must be > 0 and < interval; was $flexWindow with interval $interval"
                }
            }
        }
    }

    public companion object {
        /** Android `PeriodicWorkRequest` minimum interval. Cross-platform recommendation. */
        public val MIN_RECOMMENDED_INTERVAL: Duration = 15.minutes
    }
}
