package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MediaAsset(
    val id: String,
    val kind: Kind,
    val localPath: String? = null,
    val license: String,
    val placeholder: Boolean = false, // placeholders must be visibly identified (§5)
) {
    enum class Kind { VIDEO, ANIMATION, IMAGE, AUDIO }
}

@Serializable
data class ExerciseDefinition(
    val id: String,
    val name: String,
    val muscleGroups: List<String>,
    val equipment: Equipment,
    val difficulty: ExperienceLevel,
    val demonstration: MediaAsset? = null,
    val instructions: List<String>,
    val breathingCues: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val easierAlternativeId: String? = null,
    val harderAlternativeId: String? = null,
    val contraindicationNotes: String? = null,
    val author: String,
    val reviewedUtc: Instant,
)

@Serializable
data class WorkoutTemplate(
    val id: String,
    val ownerUID: String? = null, // null = reviewed built-in template
    val name: String,
    val exercises: List<TemplateExercise>,
    val category: String,
    val revision: Long = 1,
)

@Serializable
data class TemplateExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val restSec: Int = 90,
    val notes: String? = null,
)

@Serializable
data class TrainingPlan(
    val id: String,
    val ownerUID: String,
    val weekStartDate: String, // ISO local date
    val plannedSessions: List<PlannedSession>,
    val generatedBy: String, // "rules-v1" etc.; user edits keep provenance
    val revision: Long = 1,
)

@Serializable
data class PlannedSession(
    val id: String,
    val ownerUID: String,
    val localDate: String,
    val templateId: String?,
    val category: String,
    val location: Location? = null,
    val status: Status = Status.PLANNED,
    val revision: Long = 1,
) {
    enum class Status { PLANNED, COMPLETED, SKIPPED }
    enum class Location { GYM, HOME, OUTDOOR }
}

enum class WorkoutState { PLANNED, ACTIVE, PAUSED, COMPLETED, DISCARDED }

@Serializable
enum class SetType { STANDARD, WARM_UP, DROP, FAILURE, TIMED }

@Serializable
data class WorkoutSession(
    val id: String,
    val ownerUID: String,
    val templateId: String? = null,
    val category: String,
    val state: WorkoutState,
    val startedUtc: Instant? = null,
    val endedUtc: Instant? = null,
    val startTimeZone: String? = null,
    val sourceOffsetMinutes: Int? = null,
    val originDeviceId: String,
    val leaseOwnerDeviceId: String? = null, // canonical live controller (§13)
    val sequence: Long = 0,
    val gymVisitId: String? = null,
    val duringFast: Boolean = false,
    val source: SourceKey? = null,
    val revision: Long = 1,
    val deletedUtc: Instant? = null,
)

@Serializable
data class ExerciseSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int? = null,
    val loadKg: Double? = null, // user enters load; never inferred (§8C)
    val rpe: Double? = null,
    val rir: Double? = null,
    val durationSec: Int? = null,
    val completedUtc: Instant? = null,
    val inferredReps: Int? = null, // detector suggestion, clearly labelled
    val inferenceConfidence: Double? = null,
    val painFlag: Boolean = false,
    val notes: String? = null,
    val restSec: Int? = null,
    val tempo: String? = null,
    val setType: SetType = SetType.STANDARD,
    val groupId: String? = null, // same id groups a superset/circuit
    val substitutedForExerciseId: String? = null,
    val revision: Long = 1,
)

@Serializable
data class Interval(
    val id: String,
    val sessionId: String,
    val kind: String, // work/rest/warmup/cooldown
    val startUtc: Instant,
    val endUtc: Instant? = null,
)

@Serializable
data class RoutePoint(
    val sessionId: String,
    val utc: Instant,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double? = null,
    val accuracyM: Double? = null,
    val trimmed: Boolean = false, // private start/end trimming (§6)
)

@Serializable
data class MetricSample(
    val sessionId: String,
    val utc: Instant,
    val metric: String, // heart_rate, pace, cadence...
    val value: Double,
    val unit: String,
    val quality: String? = null,
)
