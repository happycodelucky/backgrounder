package dev.backgrounder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskIdValidationTest {
    @Test
    fun acceptsReverseDns() {
        assertEquals("com.example.sync", TaskId("com.example.sync").value)
        assertEquals("dev.backgrounder.example.sync", TaskId("dev.backgrounder.example.sync").value)
        assertEquals("a.b", TaskId("a.b").value) // minimum: at least one dot.
        assertEquals(
            "x.y.z-with_some.123",
            TaskId("x.y.z-with_some.123").value,
        )
    }

    @Test
    fun rejectsEmpty() {
        assertFailsWith<IllegalArgumentException> { TaskId("") }
    }

    @Test
    fun rejectsTooLong() {
        val tooLong = "a." + "x".repeat(TaskId.MAX_LENGTH) // strictly > MAX_LENGTH
        assertFailsWith<IllegalArgumentException> { TaskId(tooLong) }
    }

    @Test
    fun rejectsMissingDot() {
        assertFailsWith<IllegalArgumentException> { TaskId("nodothere") }
    }

    @Test
    fun rejectsLeadingOrTrailingDot() {
        assertFailsWith<IllegalArgumentException> { TaskId(".com.example") }
        assertFailsWith<IllegalArgumentException> { TaskId("com.example.") }
    }

    @Test
    fun rejectsConsecutiveDots() {
        assertFailsWith<IllegalArgumentException> { TaskId("com..example") }
    }

    @Test
    fun rejectsDisallowedCharacters() {
        assertFailsWith<IllegalArgumentException> { TaskId("com.example/sync") }
        assertFailsWith<IllegalArgumentException> { TaskId("com.example sync") }
        assertFailsWith<IllegalArgumentException> { TaskId("com.example:sync") }
    }
}
