package com.nobelle.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearGpsLabelsTest {

    @Test
    fun `required permission is exactly ACCESS_FINE_LOCATION`() {
        assertEquals(
            "android.permission.ACCESS_FINE_LOCATION",
            WearLocationPermission.REQUIRED,
        )
    }

    @Test
    fun `fix with reported accuracy rounds to whole meters`() {
        assertEquals(
            "GPS fix acquired · accuracy about 12m",
            WearGpsLabels.gpsFix(12.4f),
        )
        assertEquals(
            "GPS fix acquired · accuracy about 13m",
            WearGpsLabels.gpsFix(12.6f),
        )
    }

    @Test
    fun `fix without reported accuracy shows no fabricated number`() {
        assertEquals("GPS fix acquired", WearGpsLabels.gpsFix(null))
    }
}
