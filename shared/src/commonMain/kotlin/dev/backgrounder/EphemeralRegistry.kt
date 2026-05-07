package dev.backgrounder

import com.russhwolf.settings.Settings
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The persistent set of [TaskId]s that opted into the cold-launch sweep.
 *
 * Backed by `multiplatform-settings` (`NSUserDefaults` on Apple,
 * `SharedPreferences` on Android). Tiny, single-key — values are joined with
 * a separator to fit `Settings`' string-typed model.
 *
 * Lifecycle:
 * - [add] called by `Scheduler.schedule` when `request.ephemeral == true`.
 * - [snapshot] read by the platform-specific cold-launch sweep.
 * - [clear] called once the sweep finishes.
 * - [remove] called by `Scheduler.cancel` to keep the mirror tight.
 */
internal class EphemeralRegistry(
    private val settings: Settings,
) {
    // Guards every read-modify-write on the underlying single-key store. Without
    // this, two concurrent `add` calls can both read the pre-add snapshot and
    // race on the write — the loser's id is lost. Critical sections are short
    // and never call into suspend code, so a non-suspending lock is correct.
    // MUST NOT call suspend functions inside this block.
    private val lock = SynchronizedObject()

    fun add(taskId: TaskId) {
        synchronized(lock) {
            val current = snapshotInternal().toMutableSet()
            if (current.add(taskId)) write(current)
        }
    }

    fun remove(taskId: TaskId) {
        synchronized(lock) {
            val current = snapshotInternal().toMutableSet()
            if (current.remove(taskId)) write(current)
        }
    }

    fun snapshot(): Set<TaskId> = synchronized(lock) { snapshotInternal() }

    fun clear() {
        synchronized(lock) { settings.remove(KEY) }
    }

    private fun snapshotInternal(): Set<TaskId> {
        val raw = settings.getStringOrNull(KEY) ?: return emptySet()
        if (raw.isEmpty()) return emptySet()
        return raw
            .splitToSequence(SEPARATOR)
            .filter { it.isNotEmpty() }
            .map { TaskId(it) }
            .toSet()
    }

    private fun write(ids: Set<TaskId>) {
        if (ids.isEmpty()) {
            settings.remove(KEY)
        } else {
            settings.putString(KEY, ids.joinToString(SEPARATOR.toString()) { it.value })
        }
    }

    companion object {
        /**
         * ASCII unit separator (U+001F). Cannot occur inside a [TaskId]
         * (a-z, A-Z, 0-9, '.', '-', '_' only), so safe as a delimiter without escaping.
         * Defined via numeric conversion so the source contains no invisible characters.
         */
        internal val SEPARATOR: Char = 0x1F.toChar()
        internal const val KEY: String = "backgrounder.ephemeral_task_ids"
    }
}
