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
 */
internal class AndroidScheduledTaskQuery(
    private val workManager: WorkManager,
    private val ephemeral: EphemeralRegistry,
) {
    suspend fun snapshot(): List<ScheduledTask> {
        val query = WorkQuery.Builder.fromTags(listOf(AndroidScheduledTaskMapper.BACKGROUNDER_TAG)).build()
        // getWorkInfosFlow emits a current snapshot immediately — we want exactly that.
        val current = workManager.getWorkInfosFlow(query).firstOrNull() ?: return emptyList()
        val ephemeralIds = ephemeral.snapshot()
        return current.mapNotNull { info -> AndroidScheduledTaskMapper.toScheduledTask(info, ephemeralIds) }
    }
}
