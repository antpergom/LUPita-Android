package com.antoniopg.lupita.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {

    @Test
    fun `nothing is missing when everything is granted`() {
        val state = PermissionState(overlay = true, notifications = true)

        assertEquals(emptyList<RequiredPermission>(), state.missing())
        assertTrue(state.allGranted)
    }

    @Test
    fun `missing permissions come in the order they are requested - overlay first`() {
        val state = PermissionState(overlay = false, notifications = false)

        assertEquals(listOf(RequiredPermission.OVERLAY, RequiredPermission.NOTIFICATIONS), state.missing())
        assertFalse(state.allGranted)
    }

    @Test
    fun `only the missing one is reported`() {
        assertEquals(
            listOf(RequiredPermission.NOTIFICATIONS),
            PermissionState(overlay = true, notifications = false).missing(),
        )
        assertEquals(
            listOf(RequiredPermission.OVERLAY),
            PermissionState(overlay = false, notifications = true).missing(),
        )
    }

    @Test
    fun `notifications are mandatory - without them the bubble could not be shut down`() {
        assertFalse(PermissionState(overlay = true, notifications = false).allGranted)
    }
}
