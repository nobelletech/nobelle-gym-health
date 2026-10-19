package com.nobelle.phone.health

import com.nobelle.core.health.HealthConnectCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectPermissionsTest {

    @Test
    fun `granted strings map to our five categories only`() {
        val mapped = HealthConnectPermissions.categoriesFor(
            setOf(
                HealthConnectCategory.STEPS.permission,
                HealthConnectCategory.SLEEP.permission,
                "android.permission.health.READ_HEART_RATE", // not a supported category
                "android.permission.CAMERA",
            ),
        )
        assertEquals(
            setOf(HealthConnectCategory.STEPS, HealthConnectCategory.SLEEP),
            mapped,
        )
    }

    @Test
    fun `empty or unrelated grants map to no categories`() {
        assertTrue(HealthConnectPermissions.categoriesFor(emptySet()).isEmpty())
        assertTrue(
            HealthConnectPermissions.categoriesFor(
                setOf("android.permission.health.READ_EXERCISE"),
            ).isEmpty(),
        )
    }

    @Test
    fun `each category exposes a distinct read permission`() {
        val permissions = HealthConnectCategory.entries.map { it.permission }
        assertEquals(5, permissions.size)
        assertEquals(5, permissions.toSet().size)
        assertTrue(permissions.all { it.startsWith("android.permission.health.READ_") })
    }
}
