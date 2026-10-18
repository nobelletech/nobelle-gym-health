package com.nobelle.phone.gym

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GymVisitInputParserTest {
    @Test
    fun `valid input produces instant and trimmed fields`() {
        val result = GymVisitInputParser.parse(
            date = "2024-06-15", time = "18:30", zoneId = " America/Toronto ",
            gymLabel = "  Downtown  ", category = "  ", notes = " leg day ",
        )
        val ok = result as GymVisitInputParser.Result.Ok
        assertEquals(
            LocalDateTime(2024, 6, 15, 18, 30).toInstant(TimeZone.of("America/Toronto")),
            ok.input.start,
        )
        assertEquals("America/Toronto", ok.input.zoneId)
        assertEquals("Downtown", ok.input.gymLabel)
        assertNull(ok.input.category)
        assertEquals("leg day", ok.input.notes)
    }

    @Test
    fun `rejects invalid date`() {
        val result = GymVisitInputParser.parse(
            date = "15/06/2024", time = "18:30", zoneId = "America/Toronto",
            gymLabel = "", category = "", notes = "",
        )
        assertTrue(result is GymVisitInputParser.Result.Invalid)
    }

    @Test
    fun `rejects invalid time`() {
        val result = GymVisitInputParser.parse(
            date = "2024-06-15", time = "25:99", zoneId = "America/Toronto",
            gymLabel = "", category = "", notes = "",
        )
        assertTrue(result is GymVisitInputParser.Result.Invalid)
    }

    @Test
    fun `rejects unknown time zone`() {
        val result = GymVisitInputParser.parse(
            date = "2024-06-15", time = "18:30", zoneId = "Not/AZone",
            gymLabel = "", category = "", notes = "",
        )
        assertTrue(result is GymVisitInputParser.Result.Invalid)
    }

    @Test
    fun `nonexistent local time in a DST gap still resolves to an instant`() {
        // Toronto springs forward at 02:00 on 2024-03-10, so 02:30 never occurs;
        // kotlinx-datetime shifts the gap forward rather than failing.
        val result = GymVisitInputParser.parse(
            date = "2024-03-10", time = "02:30", zoneId = "America/Toronto",
            gymLabel = "", category = "", notes = "",
        )
        val ok = result as GymVisitInputParser.Result.Ok
        val roundTrip = ok.input.start.toLocalDateTime(TimeZone.of("America/Toronto"))
        assertEquals(3, roundTrip.hour) // shifted to 03:30 EDT
        assertEquals(30, roundTrip.minute)
        assertEquals("2024-03-10", roundTrip.date.toString())
    }
}
