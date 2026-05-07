package dev.backgrounder

/**
 * Per-invocation state handed to a [BackgroundWorker]'s `execute()` call.
 *
 * Cancellation flows through coroutine cancellation — there is *no separate
 * scope field*. To launch sibling work inside a worker, use [coroutineScope] /
 * [supervisorScope] within `execute()`.
 *
 * Constructed by the library; not user-instantiable.
 */
public class WorkerContext internal constructor(
    public val taskId: TaskId,
    /** 0-based attempt counter. Increments on each [WorkResult.Retry] cycle. */
    public val attempt: Int,
    public val input: WorkInput,
    public val capabilities: PlatformCapabilities,
)
