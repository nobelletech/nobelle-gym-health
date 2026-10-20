package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Outdoor GPS session recorded on the phone (§5 Coach "Outdoor").
 *
 * The phone's foreground tracking service collects raw GPS fixes; when the
 * session stops the fixes are compacted into [route] points and the whole
 * session becomes one immutable sync event. Route points use a compact,
 * deterministic shape — lat/lon in degrees, epochMs in milliseconds, an
 * optional horizontal accuracy estimate in metres — so identical tracks
 * serialize byte-for-byte on every device.
 */
@Serializable
data class OutdoorRoutePoint(
    val lat: Double,
    val lon: Double,
    val epochMs: Long,
    val accuracyM: Double? = null,
)

@Serializable
data class OutdoorSession(
    val id: String,
    val ownerUID: String,
    /** One of walk | run | cycle — free-form string for forward compat. */
    val activity: String,
    val startedUtc: Instant,
    val endedUtc: Instant,
    val durationSec: Long,
    val distanceMeters: Double,
    val route: List<OutdoorRoutePoint> = emptyList(),
    val pointCount: Int = route.size,
    val createdUtc: Instant,
    val updatedUtc: Instant? = null,
    val deletedUtc: Instant? = null,
    val revision: Long = 1,
) {
    companion object {
        const val ACTIVITY_WALK = "walk"
        const val ACTIVITY_RUN = "run"
        const val ACTIVITY_CYCLE = "cycle"
    }
}

/**
 * Canonical codec for the outdoor_session sync payload. ignoreUnknownKeys +
 * encodeDefaults so newer fields round-trip losslessly through older builds.
 */
object OutdoorSessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(session: OutdoorSession): String =
        json.encodeToString(OutdoorSession.serializer(), session)

    fun decode(text: String): OutdoorSession =
        json.decodeFromString(OutdoorSession.serializer(), text)
}
