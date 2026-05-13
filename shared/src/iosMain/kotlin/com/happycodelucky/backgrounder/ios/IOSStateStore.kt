package com.happycodelucky.backgrounder.ios

import co.touchlab.kermit.Logger
import com.happycodelucky.backgrounder.NetworkRequirement
import com.happycodelucky.backgrounder.TaskId
import com.happycodelucky.backgrounder.WorkInput
import com.happycodelucky.backgrounder.WorkResult
import com.russhwolf.settings.Settings
import kotlin.time.Clock

/**
 * Per-task persisted state — see plan §iOS state store.
 *
 * Backed by `multiplatform-settings` (`NSUserDefaults` on Apple). Survives
 * process death and reboot.
 *
 * Each task id has a flat namespace `tasks.<id>.*`. Concurrent writes within
 * a single task id are serialised by [IOSTaskMutexes]; cross-task writes are
 * independent.
 */
internal class IOSStateStore(
    private val settings: Settings,
) {
    private val log = Logger.withTag("Backgrounder/iOS/StateStore")

    fun writeOnSchedule(
        taskId: TaskId,
        kind: Kind,
        input: WorkInput,
        ephemeral: Boolean,
        intervalMs: Long?,
        nextRunEpochMs: Long,
        networkRequired: NetworkRequirement,
    ) {
        val k = keys(taskId)
        settings.putInt(k.schemaVersion, SCHEMA_VERSION)
        settings.putString(k.kind, kind.token)
        settings.putBoolean(k.active, true)
        settings.putBoolean(k.ephemeral, ephemeral)
        settings.putString(k.input, input.toJson())
        if (intervalMs != null) {
            settings.putLong(k.intervalMs, intervalMs)
        } else {
            settings.remove(k.intervalMs)
        }
        settings.putInt(k.attempt, 0)
        settings.putLong(k.nextRunEpochMs, nextRunEpochMs)
        settings.putString(k.networkRequired, networkRequired.name)
        settings.remove(k.lastResult)
        settings.remove(k.lastRunEpochMs)
    }

    fun readKind(taskId: TaskId): Kind? {
        val token = settings.getStringOrNull(keys(taskId).kind) ?: return null
        return Kind.entries.firstOrNull { it.token == token }
    }

    fun readActive(taskId: TaskId): Boolean = settings.getBoolean(keys(taskId).active, false)

    fun readEphemeral(taskId: TaskId): Boolean = settings.getBoolean(keys(taskId).ephemeral, false)

    /**
     * Read the persisted `networkRequired` constraint for the task.
     *
     * Returns [NetworkRequirement.None] when the key is absent — covers two
     * cases: (1) a task scheduled before schema v2 introduced the field; and
     * (2) an unknown token written by a future schema we don't understand.
     * Either way the safe default is "don't gate", preserving today's
     * "worker fires immediately" behaviour for legacy state.
     */
    fun readNetworkRequired(taskId: TaskId): NetworkRequirement {
        val token = settings.getStringOrNull(keys(taskId).networkRequired) ?: return NetworkRequirement.None
        return NetworkRequirement.entries.firstOrNull { it.name == token } ?: NetworkRequirement.None
    }

    fun readIntervalMs(taskId: TaskId): Long? {
        val k = keys(taskId)
        return if (settings.hasKey(k.intervalMs)) settings.getLong(k.intervalMs, 0L) else null
    }

    fun readAttempt(taskId: TaskId): Int = settings.getInt(keys(taskId).attempt, 0)

    fun readInput(taskId: TaskId): WorkInput {
        val raw = settings.getStringOrNull(keys(taskId).input) ?: return WorkInput.empty()
        return runCatching { WorkInput.fromJson(raw) }.getOrElse { e ->
            // Don't crash the OS handler — but make corrupted state visible. The
            // worker fires with `WorkInput.empty()`, the user can recover.
            log.e(e) { "[$taskId] failed to deserialize persisted WorkInput; falling back to empty" }
            WorkInput.empty()
        }
    }

    fun readNextRunEpochMs(taskId: TaskId): Long? {
        val k = keys(taskId)
        return if (settings.hasKey(k.nextRunEpochMs)) settings.getLong(k.nextRunEpochMs, 0L) else null
    }

    fun readLastRunEpochMs(taskId: TaskId): Long? {
        val k = keys(taskId)
        return if (settings.hasKey(k.lastRunEpochMs)) settings.getLong(k.lastRunEpochMs, 0L) else null
    }

    fun setActive(
        taskId: TaskId,
        active: Boolean,
    ) {
        settings.putBoolean(keys(taskId).active, active)
    }

    fun setAttempt(
        taskId: TaskId,
        attempt: Int,
    ) {
        settings.putInt(keys(taskId).attempt, attempt)
    }

    fun setNextRunEpochMs(
        taskId: TaskId,
        epochMs: Long,
    ) {
        settings.putLong(keys(taskId).nextRunEpochMs, epochMs)
    }

    fun recordRun(
        taskId: TaskId,
        result: WorkResult,
    ) {
        val k = keys(taskId)
        settings.putString(k.lastResult, result.toToken())
        settings.putLong(k.lastRunEpochMs, Clock.System.now().toEpochMilliseconds())
    }

    fun clear(taskId: TaskId) {
        val k = keys(taskId)
        listOf(
            k.schemaVersion,
            k.kind,
            k.active,
            k.ephemeral,
            k.intervalMs,
            k.attempt,
            k.input,
            k.lastResult,
            k.lastRunEpochMs,
            k.nextRunEpochMs,
            k.networkRequired,
        ).forEach(settings::remove)
    }

    /**
     * Every task id with an entry — derived by scanning the schema_version key
     * suffix. Returned in deterministic ascending order by [TaskId.value] so
     * [scheduled] snapshots are stable across calls (the underlying
     * `settings.keys` iteration order is platform-defined).
     */
    fun knownTaskIds(): Set<TaskId> {
        val all = settings.keys
        return all
            .asSequence()
            .filter { it.startsWith(PREFIX) && it.endsWith(SCHEMA_VERSION_SUFFIX) }
            .mapNotNull { key ->
                val raw = key.removePrefix(PREFIX).removeSuffix(SCHEMA_VERSION_SUFFIX)
                runCatching { TaskId(raw) }.getOrNull()
            }.sortedBy { it.value }
            .toCollection(LinkedHashSet())
    }

    private data class Keys(
        val schemaVersion: String,
        val kind: String,
        val active: String,
        val ephemeral: String,
        val intervalMs: String,
        val attempt: String,
        val input: String,
        val lastResult: String,
        val lastRunEpochMs: String,
        val nextRunEpochMs: String,
        val networkRequired: String,
    )

    private fun keys(taskId: TaskId): Keys {
        val base = "$PREFIX${taskId.value}."
        return Keys(
            schemaVersion = "${base}schema_version",
            kind = "${base}kind",
            active = "${base}active",
            ephemeral = "${base}ephemeral",
            intervalMs = "${base}interval_ms",
            attempt = "${base}attempt",
            input = "${base}input",
            lastResult = "${base}last_result",
            lastRunEpochMs = "${base}last_run_epoch_ms",
            nextRunEpochMs = "${base}next_run_epoch_ms",
            networkRequired = "${base}network_required",
        )
    }

    enum class Kind(
        val token: String,
    ) {
        OneShot("oneshot"),
        Periodic("periodic"),
    }

    internal companion object {
        /**
         * Schema version of the persisted per-task layout.
         *
         * - v1: kind, active, ephemeral, interval_ms, attempt, input,
         *   last_result, last_run_epoch_ms, next_run_epoch_ms.
         * - v2: adds `network_required` to drive the reachability gate
         *   (see [ReachabilityGate]). Readers default missing v1 keys to
         *   [NetworkRequirement.None], so no migration sweep is needed —
         *   legacy tasks dispatch immediately, as they did before.
         */
        internal const val SCHEMA_VERSION: Int = 2
        private const val PREFIX: String = "tasks."
        private const val SCHEMA_VERSION_SUFFIX: String = ".schema_version"
    }
}

private fun WorkResult.toToken(): String =
    when (this) {
        WorkResult.Success -> "success"
        is WorkResult.Failure -> "failure:$reason"
        WorkResult.Retry -> "retry"
    }
