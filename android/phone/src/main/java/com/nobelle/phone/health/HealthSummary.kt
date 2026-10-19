package com.nobelle.phone.health

import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One stored marker aggregated for the My Health overview: how many records
 * exist, the newest value and when it was measured, and — for additive
 * markers like steps — the total measured on the local day shown.
 */
data class MarkerSummary(
    val marker: String,
    val group: MarkerGroup,
    val unit: String,
    val count: Int,
    val latestMeasuredUtc: Instant,
    val latestValue: Double,
    val todayTotal: Double?,
)

object HealthSummary {
    /** Markers whose records describe an additive quantity over a window —
     *  summing same-day records yields a meaningful daily total. */
    private val CUMULATIVE_MARKERS = setOf("steps")

    fun summarize(
        observations: List<HealthObservation>,
        today: LocalDate,
        zone: TimeZone,
    ): List<MarkerSummary> = observations
        .filter { it.deletedUtc == null }
        .groupBy { it.marker }
        .map { (marker, records) ->
            val latest = records.maxBy { it.measuredUtc }
            val todayRecords = records.filter {
                it.measuredUtc.toLocalDateTime(zone).date == today
            }
            MarkerSummary(
                marker = marker,
                group = latest.group,
                unit = latest.unit,
                count = records.size,
                latestMeasuredUtc = latest.measuredUtc,
                latestValue = latest.value,
                todayTotal = if (marker in CUMULATIVE_MARKERS && todayRecords.isNotEmpty()) {
                    todayRecords.sumOf { it.value }
                } else {
                    null
                },
            )
        }
        .sortedWith(compareBy({ it.group.ordinal }, { it.marker }))
}
