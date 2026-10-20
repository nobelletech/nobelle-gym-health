package com.nobelle.core.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OutdoorTest {

    @Test
    fun `session payload round-trips through the codec`() {
        val session = OutdoorSession(
            id = "o1",
            ownerUID = "owner-a",
            activity = OutdoorSession.ACTIVITY_RUN,
            startedUtc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            endedUtc = Instant.fromEpochMilliseconds(1_700_001_800_000L),
            durationSec = 1_800,
            distanceMeters = 4_210.5,
            route = listOf(
                OutdoorRoutePoint(lat = 43.6532, lon = -79.3832, epochMs = 1_700_000_000_000L, accuracyM = 4.5),
                OutdoorRoutePoint(lat = 43.6540, lon = -79.3840, epochMs = 1_700_000_002_000L),
            ),
            pointCount = 2,
            createdUtc = Instant.fromEpochMilliseconds(1_700_001_800_000L),
            revision = 1,
        )

        val decoded = OutdoorSessionCodec.decode(OutdoorSessionCodec.encode(session))

        assertEquals(session, decoded)
    }

    @Test
    fun `codec tolerates unknown fields from newer builds`() {
        val text = """{"id":"o1","ownerUID":"owner-a","activity":"walk","startedUtc":"2023-11-14T22:13:20Z","endedUtc":"2023-11-14T22:23:20Z","durationSec":600,"distanceMeters":800.0,"route":[],"pointCount":0,"createdUtc":"2023-11-14T22:23:20Z","revision":1,"futureField":42}"""

        val decoded = OutdoorSessionCodec.decode(text)

        assertEquals("o1", decoded.id)
        assertEquals("walk", decoded.activity)
        assertEquals(600L, decoded.durationSec)
    }
}
