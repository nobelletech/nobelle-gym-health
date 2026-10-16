package com.nobelle.core.coaching

import com.nobelle.core.model.GymVisit
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceTest {
    private fun visit(id: String, date: String, confirmed: Boolean = true) = GymVisit(
        id = id, ownerUID = "o", startInstant = Instant.parse("2026-10-15T12:00:00Z"),
        originalTimeZone = "UTC", localAttendanceDate = date, confirmed = confirmed,
    )

    @Test
    fun `two visits same day = two visits, one attendance day`() {
        val v = listOf(visit("a", "2026-10-15"), visit("b", "2026-10-15"), visit("c", "2026-10-16"))
        assertEquals(3, Attendance.totalVisits(v))
        assertEquals(2, Attendance.uniqueGymDays(v))
    }

    @Test
    fun `unconfirmed suggestions do not count`() {
        val v = listOf(visit("a", "2026-10-15", confirmed = false))
        assertEquals(0, Attendance.totalVisits(v))
    }

    @Test
    fun `linking workouts is idempotent`() {
        val v = Attendance.linkWorkout(visit("a", "2026-10-15"), "w1")
        val v2 = Attendance.linkWorkout(v, "w1")
        assertEquals(listOf("w1"), v2.workoutIds)
        val v3 = Attendance.linkWorkout(v2, "w2")
        assertEquals(listOf("w1", "w2"), v3.workoutIds)
    }
}
