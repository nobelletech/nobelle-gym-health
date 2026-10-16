package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FastingTest {

    private fun fast(startIso: String, endIso: String? = null) = FastingSession(
        id = "f1", ownerUID = "o1",
        actualStartInstant = Instant.parse(startIso),
        actualEndInstant = endIso?.let(Instant::parse),
        originalStartZone = "America/Toronto",
        targetDurationSec = 16 * 3600,
        status = if (endIso == null) FastingSession.Status.ACTIVE else FastingSession.Status.COMPLETED,
    )

    @Test
    fun `elapsed uses true instant elapsed time not wall clock`() {
        // 2026-11-01: Toronto DST ends (clocks fall back 1h). A fast spanning the
        // change measures real elapsed time, not local-hour subtraction.
        val f = fast("2026-10-31T20:00:00Z", "2026-11-01T12:00:00Z")
        assertEquals(16.hours, f.elapsedAt(Instant.parse("2026-11-01T12:00:00Z")))
    }

    @Test
    fun `active fast elapsed derives from now`() {
        val f = fast("2026-10-15T22:00:00Z")
        assertEquals(90.minutes, f.elapsedAt(Instant.parse("2026-10-15T23:30:00Z")))
        assertFalse(f.targetReached(Instant.parse("2026-10-15T23:30:00Z")))
        assertTrue(f.targetReached(Instant.parse("2026-10-16T14:00:00Z")))
    }

    @Test
    fun `schedule requires 24h total`() {
        var threw = false
        try {
            FastingSchedule("s", "o", true, 16, 9, "20:00", listOf(1, 2, 3))
        } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `templates are selectable examples`() {
        assertEquals(listOf(12 to 12, 14 to 10, 16 to 8), FastingSchedule.TEMPLATES)
    }
}
