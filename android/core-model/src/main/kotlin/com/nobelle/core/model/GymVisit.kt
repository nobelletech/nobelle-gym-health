package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Confirmed gym attendance (§20). Separate from PlannedSession.
 * Attendance day uses the visit start's local date; overnight = one visit.
 */
@Serializable
data class GymVisit(
    val id: String,
    val ownerUID: String,
    val startInstant: Instant,
    val endInstant: Instant? = null,
    val originalTimeZone: String,
    val localAttendanceDate: String, // start's local date, ISO
    val gymLabel: String? = null,
    val workoutIds: List<String> = emptyList(),
    val category: String? = null,
    val notes: String? = null,
    val confirmed: Boolean = true, // imported/detected suggestions arrive unconfirmed
    val revision: Long = 1,
    val deletedUtc: Instant? = null,
) {
    companion object {
        fun attendanceDate(start: Instant, tz: TimeZone): String =
            start.toLocalDateTime(tz).date.toString()
    }
}
