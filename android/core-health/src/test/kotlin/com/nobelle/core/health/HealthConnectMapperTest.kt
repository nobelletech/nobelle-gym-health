package com.nobelle.core.health

import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.Provenance
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlinx.datetime.Instant

class HealthConnectMapperTest {
    private val owner = OwnerId("https://accounts.google.com", "stable-subject")
    private val importedAt = Instant.parse("2026-10-18T16:00:00Z")
    private val metadata = Metadata.manualEntry("source-record", 1)

    @Test
    fun `steps retain count end timestamp offset and source lineage`() {
        val record = StepsRecord(
            startTime = java.time.Instant.parse("2026-10-18T14:00:00Z"),
            startZoneOffset = ZoneOffset.ofHours(-4),
            endTime = java.time.Instant.parse("2026-10-18T15:00:00Z"),
            endZoneOffset = ZoneOffset.ofHours(-4),
            count = 1234,
            metadata = metadata,
        )
        val mapped = HealthConnectMapper.steps(owner, record, importedAt)
        assertEquals(1234.0, mapped.value, 0.0)
        assertEquals("count", mapped.unit)
        assertEquals(Instant.parse("2026-10-18T15:00:00Z"), mapped.measuredUtc)
        assertEquals(-14_400, mapped.timeZoneOffsetSec)
        assertEquals(MarkerGroup.ACTIVITY, mapped.group)
        assertEquals(Provenance.MANUALLY_ENTERED, mapped.provenance)
        assertEquals("Health Connect", mapped.source?.importRoute)
    }

    @Test
    fun `weight converts and labels kilograms without guessing another unit`() {
        val record = WeightRecord(
            time = java.time.Instant.parse("2026-10-18T15:00:00Z"),
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(81.25),
            metadata = metadata,
        )
        val mapped = HealthConnectMapper.weight(owner, record, importedAt)
        assertEquals(81.25, mapped.value, 0.00001)
        assertEquals("kg", mapped.unit)
        assertEquals(81.25, mapped.rawValue!!, 0.00001)
        assertEquals("kg", mapped.rawUnit)
    }

    @Test
    fun `sleep uses real elapsed minutes across offsets`() {
        val record = SleepSessionRecord(
            startTime = java.time.Instant.parse("2026-03-08T06:30:00Z"),
            startZoneOffset = ZoneOffset.ofHours(-5),
            endTime = java.time.Instant.parse("2026-03-08T13:30:00Z"),
            endZoneOffset = ZoneOffset.ofHours(-4),
            metadata = metadata,
        )
        val mapped = HealthConnectMapper.sleep(owner, record, importedAt)
        assertEquals(420.0, mapped.value, 0.00001)
        assertEquals("min", mapped.unit)
        assertEquals(Instant.parse("2026-03-08T13:30:00Z"), mapped.measuredUtc)
        assertEquals(-14_400, mapped.timeZoneOffsetSec)
    }

    @Test
    fun `future source timestamps are flagged and stable IDs include record type`() {
        val future = java.time.Instant.parse("2026-10-19T00:00:00Z")
        val heart = HealthConnectMapper.restingHeartRate(
            owner,
            RestingHeartRateRecord(future, ZoneOffset.UTC, 60, metadata),
            importedAt,
        )
        val weight = HealthConnectMapper.weight(
            owner,
            WeightRecord(future, ZoneOffset.UTC, Mass.kilograms(60.0), metadata),
            importedAt,
        )
        assertEquals(HealthObservation.QualityFlag.FUTURE_DATED, heart.qualityFlag)
        assertNotEquals(heart.id, weight.id)
    }
}
