package dev.backgrounder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkInputSizeTest {
    @Test
    fun emptyInput() {
        val input = WorkInput.empty()
        assertTrue(input.isEmpty)
        assertEquals(0, input.size)
    }

    @Test
    fun roundTripValuesByKey() {
        val input =
            WorkInput.of(
                "a" to WorkValue.StringValue("hello"),
                "b" to WorkValue.LongValue(42L),
                "c" to WorkValue.DoubleValue(3.14),
                "d" to WorkValue.BooleanValue(true),
                "e" to WorkValue.BytesValue(byteArrayOf(1, 2, 3)),
            )
        assertEquals(WorkValue.StringValue("hello"), input["a"])
        assertEquals(WorkValue.LongValue(42L), input["b"])
        assertEquals(WorkValue.DoubleValue(3.14), input["c"])
        assertEquals(WorkValue.BooleanValue(true), input["d"])
        assertEquals(WorkValue.BytesValue(byteArrayOf(1, 2, 3)), input["e"])
        assertEquals(5, input.size)
    }

    @Test
    fun rejectsEmptyKey() {
        assertFailsWith<IllegalArgumentException> {
            WorkInput.of("" to WorkValue.LongValue(1))
        }
    }

    @Test
    fun acceptsAtCap() {
        // Build a value whose serialized form is comfortably below the cap.
        val payload = "x".repeat(8000)
        val input = WorkInput.of("key" to WorkValue.StringValue(payload))
        assertTrue(input.toJson().encodeToByteArray().size <= WorkInput.MAX_SERIALIZED_BYTES)
    }

    @Test
    fun rejectsOverCap() {
        // 11 KB of string — definitely exceeds 10240-byte cap once JSON-encoded.
        val tooBig = "x".repeat(WorkInput.MAX_SERIALIZED_BYTES + 1024)
        assertFailsWith<IllegalArgumentException> {
            WorkInput.of("key" to WorkValue.StringValue(tooBig))
        }
    }

    @Test
    fun jsonRoundTripPreservesValues() {
        val input =
            WorkInput.of(
                "k1" to WorkValue.StringValue("v1"),
                "k2" to WorkValue.LongValue(7L),
            )
        val json = input.toJson()
        val parsed = WorkInput.fromJson(json)
        assertEquals(input, parsed)
    }
}
