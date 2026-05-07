package dev.backgrounder

/**
 * The result of [Scheduler.cancel] / [Scheduler.cancelAll].
 */
public sealed interface CancelOutcome {
    /**
     * The pending request(s) were cancelled.
     *
     * @property pendingCleared the count of platform-pending requests removed
     *   (best-effort — Android reports this; iOS reports 0 or 1).
     *
     * **Important**: a worker already executing on iOS is not interrupted —
     * see [PlatformCapabilities.cancelsInFlight] and
     * [SchedulerGuarantees.cancelsInFlight].
     */
    public data class Cancelled(
        val pendingCleared: Int,
    ) : CancelOutcome

    /** No work was registered with this id. */
    public data object NoSuchTask : CancelOutcome
}
