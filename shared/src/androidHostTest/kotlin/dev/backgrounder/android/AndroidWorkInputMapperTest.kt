package dev.backgrounder.android

import dev.backgrounder.TaskId
import dev.backgrounder.WorkInput
import dev.backgrounder.WorkValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure JVM tests for [AndroidWorkInputMapper] — no Robolectric needed because
 * `androidx.work.Data` is a plain bag (Parcelable yes, but constructible
 * outside the Android framework).
 */
class AndroidWorkInputMapperTest {
    private val taskId = TaskId("dev.backgrounder.test.sync")

    @Test
    fun roundTripPreservesEverything() {
        val input =
            WorkInput.of(
                "k1" to WorkValue.StringValue("hello"),
                "k2" to WorkValue.LongValue(42L),
            )
        val data = AndroidWorkInputMapper.toData(taskId, input, ephemeral = true, maxAttempts = 7)

        assertEquals(taskId, AndroidWorkInputMapper.readTaskId(data))
        assertEquals(input, AndroidWorkInputMapper.readInput(data))
        assertTrue(AndroidWorkInputMapper.readEphemeral(data))
        assertEquals(7, AndroidWorkInputMapper.readMaxAttempts(data))
    }

    @Test
    fun emptyInputRoundTrips() {
        val data =
            AndroidWorkInputMapper.toData(
                taskId,
                WorkInput.empty(),
                ephemeral = false,
                maxAttempts = 10,
            )
        assertEquals(taskId, AndroidWorkInputMapper.readTaskId(data))
        assertEquals(WorkInput.empty(), AndroidWorkInputMapper.readInput(data))
        assertEquals(false, AndroidWorkInputMapper.readEphemeral(data))
        assertEquals(10, AndroidWorkInputMapper.readMaxAttempts(data))
    }

    @Test
    fun ephemeralDefaultsFalseAndMaxAttemptsDefaultsHigh() {
        // Given a Data missing our keys, defaults should be safe.
        val data = androidx.work.Data.EMPTY
        assertEquals(null, AndroidWorkInputMapper.readTaskId(data))
        assertEquals(WorkInput.empty(), AndroidWorkInputMapper.readInput(data))
        assertEquals(false, AndroidWorkInputMapper.readEphemeral(data))
        assertEquals(Int.MAX_VALUE, AndroidWorkInputMapper.readMaxAttempts(data))
    }
}
