package com.nobelle.phone.health

import com.nobelle.core.model.MarkerGroup

/**
 * Validated input for a manually recorded health marker. [minutesAgo] is
 * applied by the ViewModel against the current clock to set [measuredUtc].
 */
data class ManualEntryInput(
    val marker: String,
    val value: Double,
    val minutesAgo: Int,
)

/**
 * Fixed markers offered for manual entry. Values are stored normalised to the
 * unit shown; raw value/unit are kept identical for manual records.
 */
object ManualHealthMarkers {
    data class Marker(
        val marker: String,
        val unit: String,
        val group: MarkerGroup,
        val label: String,
        val min: Double,
        val max: Double,
    )

    val ALL: List<Marker> = listOf(
        Marker("steps", "count", MarkerGroup.ACTIVITY, "Steps", 0.0, 100_000.0),
        Marker("resting_heart_rate", "bpm", MarkerGroup.HEART_VITALS, "Resting heart rate", 0.0, 300.0),
        Marker("weight", "kg", MarkerGroup.BODY, "Weight", 0.0, 500.0),
        Marker("oxygen_saturation", "%", MarkerGroup.CARDIORESPIRATORY, "Oxygen saturation", 0.0, 100.0),
        Marker("blood_pressure_systolic", "mmHg", MarkerGroup.BLOOD_PRESSURE, "Blood pressure — systolic", 0.0, 300.0),
        Marker("blood_pressure_diastolic", "mmHg", MarkerGroup.BLOOD_PRESSURE, "Blood pressure — diastolic", 0.0, 200.0),
        Marker("body_temperature", "°C", MarkerGroup.TEMPERATURE, "Body temperature", 0.0, 50.0),
    )

    private val BY_MARKER = ALL.associateBy { it.marker }

    fun find(marker: String): Marker? = BY_MARKER[marker]
}

/**
 * Pure parser for the manual health-marker form. Rejects non-numeric,
 * infinite or out-of-range values before any domain write happens.
 */
object ManualEntryInputParser {
    sealed interface Result {
        data class Ok(val input: ManualEntryInput) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(
        marker: String,
        value: String,
        minutesAgo: String,
    ): Result {
        val markerDef = ManualHealthMarkers.find(marker)
            ?: return Result.Invalid("Choose a marker from the list")

        val parsedValue = value.trim().toDoubleOrNull()
            ?: return Result.Invalid("Enter a number for ${markerDef.label}")
        if (parsedValue.isNaN() || parsedValue.isInfinite()) {
            return Result.Invalid("Enter a finite number for ${markerDef.label}")
        }
        if (parsedValue < markerDef.min || parsedValue > markerDef.max) {
            return Result.Invalid("${markerDef.label} must be between ${markerDef.min.toInt()} and ${markerDef.max.toInt()} ${markerDef.unit}")
        }

        val parsedMinutes = minutesAgo.trim().toIntOrNull()
            ?: return Result.Invalid("Minutes ago must be a whole number")
        if (parsedMinutes < 0) {
            return Result.Invalid("Minutes ago cannot be negative")
        }
        if (parsedMinutes > MAX_MINUTES_AGO) {
            return Result.Invalid("Measured time is too far in the past")
        }

        return Result.Ok(
            ManualEntryInput(
                marker = marker,
                value = parsedValue,
                minutesAgo = parsedMinutes,
            )
        )
    }

    private const val MAX_MINUTES_AGO = 365 * 24 * 60
}
