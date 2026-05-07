package dev.backgrounder.android

import androidx.work.Data
import dev.backgrounder.TaskId
import dev.backgrounder.WorkInput

/**
 * Round-trip [WorkInput] ↔ [Data].
 *
 * We don't try to project each [WorkValue] subtype onto Android's [Data] schema
 * one-by-one. Instead the entire [WorkInput] is JSON-serialised and stored
 * under a single string key. Reasons:
 *
 * 1. [Data]'s primitive set is narrower than [WorkValue]'s and missing
 *    [WorkValue.BytesValue] coverage in some Android versions; JSON is
 *    uniform.
 * 2. The size cap is enforced at [WorkInput.of] time using the same
 *    serializer, so we don't risk "fits one shape, doesn't fit the other."
 * 3. [Data] also gets the [TaskId] alongside, since [RegistryDispatchWorker]
 *    is the single bridge worker class — it must read the task id from
 *    `inputData` to know which factory to invoke.
 */
internal object AndroidWorkInputMapper {
    internal const val KEY_TASK_ID: String = "_backgrounder.task_id"
    internal const val KEY_INPUT_JSON: String = "_backgrounder.input_json"
    internal const val KEY_EPHEMERAL: String = "_backgrounder.ephemeral"
    internal const val KEY_MAX_ATTEMPTS: String = "_backgrounder.max_attempts"

    fun toData(
        taskId: TaskId,
        input: WorkInput,
        ephemeral: Boolean,
        maxAttempts: Int,
    ): Data =
        Data
            .Builder()
            .putString(KEY_TASK_ID, taskId.value)
            .putString(KEY_INPUT_JSON, input.toJson())
            .putBoolean(KEY_EPHEMERAL, ephemeral)
            .putInt(KEY_MAX_ATTEMPTS, maxAttempts)
            .build()

    fun readTaskId(data: Data): TaskId? = data.getString(KEY_TASK_ID)?.let { TaskId(it) }

    fun readInput(data: Data): WorkInput {
        val json = data.getString(KEY_INPUT_JSON) ?: return WorkInput.empty()
        return WorkInput.fromJson(json)
    }

    fun readEphemeral(data: Data): Boolean = data.getBoolean(KEY_EPHEMERAL, false)

    fun readMaxAttempts(data: Data): Int = data.getInt(KEY_MAX_ATTEMPTS, Int.MAX_VALUE)
}
