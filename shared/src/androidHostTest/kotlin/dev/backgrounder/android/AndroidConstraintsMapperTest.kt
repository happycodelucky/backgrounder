package dev.backgrounder.android

import androidx.work.NetworkType
import dev.backgrounder.NetworkRequirement
import dev.backgrounder.WorkConstraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidConstraintsMapperTest {
    @Test
    fun defaultsAreLeastRestrictive() {
        val c = WorkConstraints().toWorkManagerConstraints()
        assertEquals(NetworkType.NOT_REQUIRED, c.requiredNetworkType)
        assertFalse(c.requiresCharging())
    }

    @Test
    fun networkAnyMapsToConnected() {
        val c = WorkConstraints(networkRequired = NetworkRequirement.Any).toWorkManagerConstraints()
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
    }

    @Test
    fun networkUnmeteredMapsToUnmetered() {
        val c = WorkConstraints(networkRequired = NetworkRequirement.Unmetered).toWorkManagerConstraints()
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
    }

    @Test
    fun chargingPropagates() {
        val c = WorkConstraints(requiresCharging = true).toWorkManagerConstraints()
        assertTrue(c.requiresCharging())
    }
}
