package com.nobelle.phone.fasting

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Validated input for a manually recorded, already-completed fast. */
data class FastingBackdateInput(
    val start: Instant,
    val end: Instant,
    val startZone: String,
    val endZone: String,
    val notes: String?,
)

/**
 * Pure parser for the backdated-fast form. Local dates/times are resolved in
 * the chosen zone; elapsed time is always the difference of real instants,
 * so DST transitions are handled correctly.
 */
object FastingInputParser {
    sealed interface Result {
        data class Ok(val input: FastingBackdateInput) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String,
        zoneId: String,
        notes: String,
    ): Result {
        val zone = try {
            TimeZone.of(zoneId.trim())
        } catch (failure: Exception) {
            return Result.Invalid("Time zone is not recognised")
        }
        val startLocal = localDateTime(startDate, startTime)
            ?: return Result.Invalid("Start must be YYYY-MM-DD and HH:mm")
        val endLocal = localDateTime(endDate, endTime)
            ?: return Result.Invalid("End must be YYYY-MM-DD and HH:mm")
        val start = startLocal.toInstant(zone)
        val end = endLocal.toInstant(zone)
        if (end < start) return Result.Invalid("End must not be before start")
        return Result.Ok(
            FastingBackdateInput(
                start = start,
                end = end,
                startZone = zone.id,
                endZone = zone.id,
                notes = notes.trim().ifBlank { null },
            )
        )
    }

    private fun localDateTime(date: String, time: String): LocalDateTime? {
        val localDate = try {
            LocalDate.parse(date.trim())
        } catch (failure: Exception) {
            return null
        }
        val localTime = try {
            LocalTime.parse(time.trim())
        } catch (failure: Exception) {
            return null
        }
        return LocalDateTime(localDate, localTime)
    }
}
