package com.nobelle.phone.gym

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Validated gym-visit form input. */
data class GymVisitInput(
    val start: Instant,
    val zoneId: String,
    val gymLabel: String?,
    val category: String?,
    val notes: String?,
)

/**
 * Pure parser for the backdate/edit visit form. Date and time are entered as
 * local wall-clock values in the given zone; the instant is resolved through
 * kotlinx-datetime so DST gaps/overlaps follow platform rules. Anything
 * unparseable is rejected before the repository is ever called.
 */
object GymVisitInputParser {
    sealed interface Result {
        data class Ok(val input: GymVisitInput) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(
        date: String,
        time: String,
        zoneId: String,
        gymLabel: String,
        category: String,
        notes: String,
    ): Result {
        val zone = try {
            TimeZone.of(zoneId.trim())
        } catch (failure: Exception) {
            return Result.Invalid("Time zone is not recognised")
        }
        val localDate = try {
            LocalDate.parse(date.trim())
        } catch (failure: Exception) {
            return Result.Invalid("Date must be YYYY-MM-DD")
        }
        val localTime = try {
            LocalTime.parse(time.trim())
        } catch (failure: Exception) {
            return Result.Invalid("Time must be HH:mm")
        }
        return Result.Ok(
            GymVisitInput(
                start = LocalDateTime(localDate, localTime).toInstant(zone),
                zoneId = zone.id,
                gymLabel = gymLabel.trim().ifBlank { null },
                category = category.trim().ifBlank { null },
                notes = notes.trim().ifBlank { null },
            )
        )
    }
}
