package com.nobelle.phone.fasting

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastingInputParserTest {
    private val zone = "America/Toronto"

    @Test
    fun `valid input produces instants and trimmed notes`() {
        val result = FastingInputParser.parse(
            startDate = "2024-06-14", startTime = "20:00",
            endDate = "2024-06-15", endTime = "12:30",
            zoneId = " $zone ", notes = "  felt fine  ",
        )
        val ok = result as FastingInputParser.Result.Ok
        val tz = TimeZone.of(zone)
        assertEquals(LocalDateTime(2024, 6, 14, 20, 0).toInstant(tz), ok.input.start)
        assertEquals(LocalDateTime(2024, 6, 15, 12, 30).toInstant(tz), ok.input.end)
        assertEquals(zone, ok.input.startZone)
        assertEquals(zone, ok.input.endZone)
        assertEquals("felt fine", ok.input.notes)
    }

    @Test
    fun `rejects invalid date and time`() {
        val badDate = FastingInputParser.parse(
            startDate = "14/06/2024", startTime = "20:00",
            endDate = "2024-06-15", endTime = "12:30",
            zoneId = zone, notes = "",
        )
        assertTrue(badDate is FastingInputParser.Result.Invalid)

        val badTime = FastingInputParser.parse(
            startDate = "2024-06-14", startTime = "8pm",
            endDate = "2024-06-15", endTime = "12:30",
            zoneId = zone, notes = "",
        )
        assertTrue(badTime is FastingInputParser.Result.Invalid)
    }

    @Test
    fun `rejects unknown zone and end before start`() {
        val badZone = FastingInputParser.parse(
            startDate = "2024-06-14", startTime = "20:00",
            endDate = "2024-06-15", endTime = "12:30",
            zoneId = "Not/AZone", notes = "",
        )
        assertTrue(badZone is FastingInputParser.Result.Invalid)

        val reversed = FastingInputParser.parse(
            startDate = "2024-06-15", startTime = "12:30",
            endDate = "2024-06-14", endTime = "20:00",
            zoneId = zone, notes = "",
        )
        assertTrue(reversed is FastingInputParser.Result.Invalid)
    }

    @Test
    fun `elapsed uses real instants across a DST gap`() {
        // Toronto springs forward 02:00 -> 03:00 on 2024-03-10: wall clock
        // 01:30 -> 03:30 spans one real hour, not two.
        val result = FastingInputParser.parse(
            startDate = "2024-03-10", startTime = "01:30",
            endDate = "2024-03-10", endTime = "03:30",
            zoneId = zone, notes = "",
        )
        val ok = result as FastingInputParser.Result.Ok
        assertEquals(3600L, (ok.input.end - ok.input.start).inWholeSeconds)
    }

    @Test
    fun `blank notes become null`() {
        val result = FastingInputParser.parse(
            startDate = "2024-06-14", startTime = "20:00",
            endDate = "2024-06-15", endTime = "08:00",
            zoneId = zone, notes = "   ",
        )
        assertNull((result as FastingInputParser.Result.Ok).input.notes)
    }
}
