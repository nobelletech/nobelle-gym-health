package com.nobelle.phone.outdoor

import com.nobelle.core.model.OutdoorRoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMathTest {

    @Test
    fun `haversine of identical points is zero`() {
        assertEquals(0.0, TrackMath.haversineMeters(43.6532, -79.3832, 43.6532, -79.3832), 1e-9)
    }

    @Test
    fun `haversine matches a known short distance`() {
        // ~111.2 m per 0.001 degrees of latitude.
        val d = TrackMath.haversineMeters(43.6532, -79.3832, 43.6542, -79.3832)
        assertEquals(111.2, d, 1.0)
    }

    @Test
    fun `haversine matches a known long distance`() {
        // Toronto -> Montreal is roughly 503 km great-circle.
        val d = TrackMath.haversineMeters(43.6532, -79.3832, 45.5019, -73.5674)
        assertEquals(503_000.0, d, 5_000.0)
    }

    @Test
    fun `fixes worse than the accuracy threshold are unusable`() {
        assertFalse(TrackMath.usable(OutdoorRoutePoint(1.0, 1.0, 0L, accuracyM = 45.0)))
        assertFalse(TrackMath.usable(OutdoorRoutePoint(1.0, 1.0, 0L, accuracyM = 30.1)))
        assertTrue(TrackMath.usable(OutdoorRoutePoint(1.0, 1.0, 0L, accuracyM = 30.0)))
        assertTrue(TrackMath.usable(OutdoorRoutePoint(1.0, 1.0, 0L, accuracyM = null)))
    }

    @Test
    fun `distance sums consecutive usable fixes and skips bad ones`() {
        val points = listOf(
            OutdoorRoutePoint(43.6532, -79.3832, 0L, accuracyM = 5.0),
            // Wild jump with poor accuracy — must not add distance either way.
            OutdoorRoutePoint(44.0, -80.0, 1_000L, accuracyM = 250.0),
            OutdoorRoutePoint(43.6542, -79.3832, 2_000L, accuracyM = 5.0),
            OutdoorRoutePoint(43.6552, -79.3832, 4_000L, accuracyM = 5.0),
        )
        val d = TrackMath.distanceMeters(points)
        assertEquals(2 * 111.2, d, 2.0)
    }

    @Test
    fun `distance of a single point is zero`() {
        assertEquals(
            0.0,
            TrackMath.distanceMeters(listOf(OutdoorRoutePoint(43.0, -79.0, 0L))),
            1e-9,
        )
    }
}
