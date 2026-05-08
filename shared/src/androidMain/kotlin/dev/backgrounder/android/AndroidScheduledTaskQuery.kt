package dev.backgrounder.android

import androidx.work.WorkManager
import androidx.work.WorkQuery
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.ScheduledTask
import kotlinx.coroutines.flow.firstOrNull

/**
 * I/O-shaped layer around `WorkManager.getWorkInfosFlow`.
 *
 * Filters by the canonical Backgrounder tag so it doesn't pick up unrelated
 * work the user might have scheduled with the same `WorkManager` instance.
 * The pure WorkInfo → ScheduledTask transform lives in
 * [AndroidScheduledTaskMapper] and is unit-tested independently of WorkManager.
 *
 * The `WorkManager` dependency is held as a `() -> WorkManager` provider so
 * we don't trigger `WorkManager.getInstance(...)` at construction time —
 * resolving early would lock the WorkManager configuration before the user's
 * `Configuration.Provider.workManagerConfiguration` had a chance to install
 * our [BackgrounderWorkerFactory] (plan §"DI-free initialization" §2.3).
 *
 * Two constructor shapes:
 *  - The public `(WorkManager, EphemeralRegistry)` constructor for the legacy
 *    Koin module path, which already has a fully-configured `WorkManager`.
 *    Will be deleted in Step 5 of the redesign.
 *  - [Companion.withProvider] for the new [AndroidBackgrounderBuilder].
 */
internal class AndroidScheduledTaskQuery private constructor(
    private val workManagerProvider: () -> WorkManager,
    private val ephemeral: EphemeralRegistry,
) {
    /**
     * Eager-WorkManager constructor for the legacy Koin module path.
     * Removed in Step 5 of the DI-free init redesign.
     */
    constructor(workManager: WorkManager, ephemeral: EphemeralRegistry) :
        this(workManagerProvider = { workManager }, ephemeral = ephemeral)

    private val workManager: WorkManager get() = workManagerProvider()

    suspend fun snapshot(): List<ScheduledTask> {
        val query = WorkQuery.Builder.fromTags(listOf(AndroidScheduledTaskMapper.BACKGROUNDER_TAG)).build()
        // getWorkInfosFlow emits a current snapshot immediately — we want exactly that.
        val current = workManager.getWorkInfosFlow(query).firstOrNull() ?: return emptyList()
        val ephemeralIds = ephemeral.snapshot()
        return current.mapNotNull { info -> AndroidScheduledTaskMapper.toScheduledTask(info, ephemeralIds) }
    }

    internal companion object {
        /**
         * Lazy-WorkManager factory for the new [AndroidBackgrounderBuilder].
         * The provider is invoked on every `snapshot()` call.
         */
        fun withProvider(
            workManagerProvider: () -> WorkManager,
            ephemeral: EphemeralRegistry,
        ): AndroidScheduledTaskQuery = AndroidScheduledTaskQuery(workManagerProvider = workManagerProvider, ephemeral = ephemeral)
    }
}
