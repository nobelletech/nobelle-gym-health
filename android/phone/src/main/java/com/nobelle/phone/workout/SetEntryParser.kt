package com.nobelle.phone.workout

import com.nobelle.core.model.SetType

/** Fully validated set entry produced from raw form text. */
data class SetEntryInput(
    val exerciseId: String,
    val reps: Int?,
    val loadKg: Double?,
    val durationSec: Int?,
    val restSec: Int?,
    val rpe: Double?,
    val rir: Double?,
    val tempo: String?,
    val painFlag: Boolean,
    val notes: String?,
    val setType: SetType = SetType.STANDARD,
    val groupId: String? = null,
    val substitutedForExerciseId: String? = null,
)

/**
 * Pure parser for the manual set-entry form. Blank fields become null;
 * anything unparseable or out of range is rejected before the repository
 * is ever called.
 */
object SetEntryParser {
    sealed interface Result {
        data class Ok(val entry: SetEntryInput) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(
        exerciseId: String,
        reps: String,
        loadKg: String,
        durationSec: String,
        restSec: String,
        rpe: String,
        rir: String,
        tempo: String,
        painFlag: Boolean,
        notes: String,
        setType: SetType = SetType.STANDARD,
        groupId: String = "",
        substitutedForExerciseId: String = "",
    ): Result {
        val trimmedExercise = exerciseId.trim()
        if (trimmedExercise.isBlank()) return Result.Invalid("Exercise is required")
        val fields = Fields()
        val parsedReps = fields.integer(reps, "reps", min = 0)
        val parsedLoad = fields.decimal(loadKg, "load kg", min = 0.0)
        val parsedDuration = fields.integer(durationSec, "duration", min = 0)
        val parsedRest = fields.integer(restSec, "rest", min = 0)
        val parsedRpe = fields.decimal(rpe, "RPE", min = 0.0, max = 10.0)
        val parsedRir = fields.decimal(rir, "RIR", min = 0.0)
        fields.failedLabel?.let { return Result.Invalid("$it must be a valid number in range") }
        return Result.Ok(
            SetEntryInput(
                exerciseId = trimmedExercise,
                reps = parsedReps,
                loadKg = parsedLoad,
                durationSec = parsedDuration,
                restSec = parsedRest,
                rpe = parsedRpe,
                rir = parsedRir,
                tempo = tempo.trim().ifBlank { null },
                painFlag = painFlag,
                notes = notes.trim().ifBlank { null },
                setType = setType,
                groupId = groupId.trim().ifBlank { null },
                substitutedForExerciseId = substitutedForExerciseId.trim().ifBlank { null },
            )
        )
    }

    private class Fields {
        var failedLabel: String? = null

        fun integer(raw: String, label: String, min: Int): Int? {
            if (failedLabel != null || raw.isBlank()) return null
            val value = raw.trim().toIntOrNull()
            if (value == null || value < min) {
                failedLabel = label
                return null
            }
            return value
        }

        fun decimal(raw: String, label: String, min: Double, max: Double = Double.MAX_VALUE): Double? {
            if (failedLabel != null || raw.isBlank()) return null
            val value = raw.trim().toDoubleOrNull()
            if (value == null || value < min || value > max) {
                failedLabel = label
                return null
            }
            return value
        }
    }
}
