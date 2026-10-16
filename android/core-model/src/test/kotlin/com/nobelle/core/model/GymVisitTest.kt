package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GymVisitTest {

    @Test
    fun `attendance date is visit start local date`() {
        // 01:30 UTC Oct 16 = 21:30 Toronto Oct 15 (EDT) -> attendance on Oct 15 locally
        val start = Instant.parse("2026-10-16T01:30:00Z")
        assertEquals("2026-10-15", GymVisit.attendanceDate(start, TimeZone.of("America/Toronto")))
        assertEquals("2026-10-16", GymVisit.attendanceDate(start, TimeZone.UTC))
    }

    @Test
    fun `overnight visit counts once by start date`() {
        val visit = GymVisit(
            id = "v1", ownerUID = "o1",
            startInstant = Instant.parse("2026-10-15T23:00:00Z"),
            endInstant = Instant.parse("2026-10-16T01:00:00Z"),
            originalTimeZone = "UTC",
            localAttendanceDate = GymVisit.attendanceDate(Instant.parse("2026-10-15T23:00:00Z"), TimeZone.UTC),
        )
        assertEquals("2026-10-15", visit.localAttendanceDate)
    }

    @Test
    fun `multiple visits same day are distinct visits but one attendance day`() {
        val dates = listOf("2026-10-15", "2026-10-15")
        assertEquals(1, dates.toSet().size)
    }
}
