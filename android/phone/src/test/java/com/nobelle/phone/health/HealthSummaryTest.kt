package com.nobelle.phone.health

import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.Provenance
import com.nobelle.core.model.RecordingMethod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSummaryTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2024, 6, 15)

    private fun observation(
        marker: String,
        value: Double,
        measured: Instant,
        group: MarkerGroup = MarkerGroup.ACTIVITY,
        unit: String = "count",
        deleted: Instant? = null,
    ) = HealthObservation(
        id = "$marker-$value-${measured.epochSeconds}",
        ownerUID = "issuer:subject",
        marker = marker,
        group = group,
        value = value,
        unit = unit,
        measuredUtc = measured,
        provenance = Provenance.MEASURED_IMPORTED,
        method = RecordingMethod.SOURCE_SYNC,
        deletedUtc = deleted,
    )

    @Test
    fun `groups records per marker with count and latest measurement`() {
        val summaries = HealthSummary.summarize(
            observations = listOf(
                observation("weight", 81.0, Instant.parse("2024-06-10T08:00:00Z"),
                    group = MarkerGroup.BODY, unit = "kg"),
                observation("weight", 80.5, Instant.parse("2024-06-14T08:00:00Z"),
                    group = MarkerGroup.BODY, unit = "kg"),
                observation("steps", 3000.0, Instant.parse("2024-06-14T12:00:00Z")),
            ),
            today = today,
            zone = zone,
        )
        val weight = summaries.single { it.marker == "weight" }
        assertEquals(2, weight.count)
        assertEquals(Instant.parse("2024-06-14T08:00:00Z"), weight.latestMeasuredUtc)
        assertEquals(80.5, weight.latestValue, 0.0001)
        assertEquals("kg", weight.unit)
        val steps = summaries.single { it.marker == "steps" }
        assertEquals(1, steps.count)
    }

    @Test
    fun `steps total sums only records measured on the given day`() {
        val summaries = HealthSummary.summarize(
            observations = listOf(
                observation("steps", 2000.0, Instant.parse("2024-06-15T10:00:00Z")),
                observation("steps", 1500.0, Instant.parse("2024-06-15T18:00:00Z")),
                observation("steps", 9000.0, Instant.parse("2024-06-14T18:00:00Z")),
            ),
            today = today,
            zone = zone,
        )
        assertEquals(3500.0, summaries.single().todayTotal!!, 0.0001)
    }

    @Test
    fun `non-cumulative markers and markers without records today have no total`() {
        val summaries = HealthSummary.summarize(
            observations = listOf(
                observation("weight", 80.0, Instant.parse("2024-06-15T08:00:00Z"),
                    group = MarkerGroup.BODY, unit = "kg"),
                observation("steps", 4000.0, Instant.parse("2024-06-14T18:00:00Z")),
            ),
            today = today,
            zone = zone,
        )
        assertNull(summaries.single { it.marker == "weight" }.todayTotal)
        assertNull(summaries.single { it.marker == "steps" }.todayTotal)
    }

    @Test
    fun `deleted observations are excluded`() {
        val summaries = HealthSummary.summarize(
            observations = listOf(
                observation("steps", 100.0, Instant.parse("2024-06-15T10:00:00Z"),
                    deleted = Instant.parse("2024-06-15T11:00:00Z")),
            ),
            today = today,
            zone = zone,
        )
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `summaries sort by marker group then marker name`() {
        val summaries = HealthSummary.summarize(
            observations = listOf(
                observation("weight", 80.0, Instant.parse("2024-06-15T08:00:00Z"),
                    group = MarkerGroup.BODY, unit = "kg"),
                observation("steps", 100.0, Instant.parse("2024-06-15T10:00:00Z")),
            ),
            today = today,
            zone = zone,
        )
        assertEquals(listOf("steps", "weight"), summaries.map { it.marker })
    }
}
