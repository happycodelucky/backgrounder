package dev.backgrounder.android

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidEphemeralReadyTest {
    @BeforeTest
    @AfterTest
    fun reset() {
        AndroidEphemeralReady.reset()
    }

    @Test
    fun startsFalse() {
        assertFalse(AndroidEphemeralReady.snapshot())
    }

    @Test
    fun markReadyFlipsTrue() {
        AndroidEphemeralReady.markReady()
        assertTrue(AndroidEphemeralReady.snapshot())
    }

    @Test
    fun resetReturnsToFalse() {
        AndroidEphemeralReady.markReady()
        AndroidEphemeralReady.reset()
        assertFalse(AndroidEphemeralReady.snapshot())
    }
}
