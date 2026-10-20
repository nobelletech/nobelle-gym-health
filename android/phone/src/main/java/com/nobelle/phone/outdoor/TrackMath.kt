package com.nobelle.phone.outdoor

import com.nobelle.core.model.OutdoorRoutePoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure track math for outdoor sessions — no Android types, so the exact code
 * the service uses is unit-tested on the JVM.
 *
 * Fixes whose reported horizontal accuracy is worse than [MAX_ACCURACY_M]
 * metres are ignored for distance accumulation: consumer GPS occasionally
 * emits multi-hundred-metre jumps while stationary, and summing them would
 * fabricate distance. Fixes that report no accuracy at all are kept — the
 * provider gave no reason to doubt them.
 */
object TrackMath {
    const val MAX_ACCURACY_M = 30.0

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** True when a fix is trustworthy enough to extend the track. */
    fun usable(point: OutdoorRoutePoint): Boolean =
        point.accuracyM.let { it == null || it <= MAX_ACCURACY_M }

    /**
     * Total distance along [points], skipping unusable fixes. Distances are
     * measured between consecutive *usable* points so a rejected fix does not
     * add a phantom jump on either side of itself.
     */
    fun distanceMeters(points: List<OutdoorRoutePoint>): Double {
        var total = 0.0
        var previous: OutdoorRoutePoint? = null
        for (point in points) {
            if (!usable(point)) continue
            val last = previous
            if (last != null) {
                total += haversineMeters(last.lat, last.lon, point.lat, point.lon)
            }
            previous = point
        }
        return total
    }
}
