package com.nobelle.wear

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WearHeartRatePermissionsTest {

    @Test
    fun `api 35 and below request BODY_SENSORS only`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.BODY_SENSORS),
            WearHeartRatePermissions.forSdk(35),
        )
        assertArrayEquals(
            arrayOf(Manifest.permission.BODY_SENSORS),
            WearHeartRatePermissions.forSdk(33),
        )
    }

    @Test
    fun `api 36 and above request READ_HEART_RATE only`() {
        assertArrayEquals(
            arrayOf("android.permission.health.READ_HEART_RATE"),
            WearHeartRatePermissions.forSdk(36),
        )
        assertArrayEquals(
            arrayOf("android.permission.health.READ_HEART_RATE"),
            WearHeartRatePermissions.forSdk(37),
        )
    }

    @Test
    fun `the two permissions are never combined`() {
        listOf(30, 35, 36).forEach { sdk ->
            val permissions = WearHeartRatePermissions.forSdk(sdk)
            assertEquals(1, permissions.size)
        }
    }
}
