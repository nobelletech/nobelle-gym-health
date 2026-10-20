package com.nobelle.phone.outdoor

import com.nobelle.core.model.OutdoorRoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory bridge between [OutdoorTrackService] (which owns the GPS
 * listener) and [OutdoorViewModel] (which persists the finished session).
 * Process death loses the in-progress track — acceptable for a
 * user-controlled recording, matching how an activity recreate already works.
 */
object OutdoorTrackStore {

    data class FinishedTrack(
        val activity: String,
        val startedAtMs: Long,
        val endedAtMs: Long,
        val points: List<OutdoorRoutePoint>,
        val distanceMeters: Double,
    )

    data class TrackState(
        val tracking: Boolean = false,
        val activity: String? = null,
        val startedAtMs: Long? = null,
        val points: List<OutdoorRoutePoint> = emptyList(),
        val distanceMeters: Double = 0.0,
        /** Set once when tracking stops; cleared after persistence. */
        val finished: FinishedTrack? = null,
    )

    private val mutableState = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = mutableState

    fun begin(activity: String, startedAtMs: Long) {
        mutableState.value = TrackState(tracking = true, activity = activity, startedAtMs = startedAtMs)
    }

    /** Appends a raw fix and re-accumulates distance over usable points. */
    fun addPoint(point: OutdoorRoutePoint) {
        val current = mutableState.value
        if (!current.tracking) return
        val points = current.points + point
        mutableState.value = current.copy(
            points = points,
            distanceMeters = TrackMath.distanceMeters(points),
        )
    }

    fun finish(endedAtMs: Long) {
        val current = mutableState.value
        if (!current.tracking) return
        mutableState.value = current.copy(
            tracking = false,
            finished = FinishedTrack(
                activity = current.activity ?: "walk",
                startedAtMs = current.startedAtMs ?: endedAtMs,
                endedAtMs = endedAtMs,
                points = current.points,
                distanceMeters = current.distanceMeters,
            ),
        )
    }

    fun clearFinished() {
        mutableState.value = TrackState()
    }
}
