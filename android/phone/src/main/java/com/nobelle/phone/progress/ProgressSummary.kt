package com.nobelle.phone.progress

import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.WatchWorkoutPayload
import com.nobelle.core.model.WorkoutState
import com.nobelle.phone.health.HealthSummary
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Counts for one local-date window, [start] through [end] inclusive —
 * an ISO week when produced by [ProgressSummary].
 * [steps] is null when no additive record falls inside the window, keeping
 * "no data" distinct from a real zero (§10).
 */
data class WeekSummary(
    val workouts: Int = 0,
    val sets: Int = 0,
    val gymDays: Int = 0,
    val completedFasts: Int = 0,
    val watchWorkouts: Int = 0,
    val watchDurationSec: Long = 0,
    val steps: Double? = null,
)

/** The heaviest confirmed set logged for one exercise — load and reps > 0. */
data class PersonalBest(
    val exerciseId: String,
    val loadKg: Double,
    val reps: Int,
)

/** Display-ready snapshot for the Progress tab: current and previous ISO
 *  week plus all-time rollups of attendance and personal bests. */
data class ProgressReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val thisWeek: WeekSummary,
    val lastWeek: WeekSummary,
    val personalBests: List<PersonalBest>,
    val totalGymDays: Int,
)

/**
 * Pure aggregation for the Progress tab (§3). Every count derives only from
 * records already stored under the owner identity; the caller supplies the
 * device-local [today] and [zone] so results are deterministic and testable.
 * Week windows are ISO weeks — Monday through Sunday.
 */
object ProgressSummary {

    fun summarize(
        workouts: List<WorkoutPayload>,
        gymVisits: List<GymVisit>,
        fasts: List<FastingSession>,
        watchWorkouts: List<WatchWorkoutPayload>,
        observations: List<HealthObservation>,
        today: LocalDate,
        zone: TimeZone,
        personalBestLimit: Int = 5,
    ): ProgressReport {
        val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
        val weekEnd = weekStart.plus(DatePeriod(days = 6))
        return ProgressReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            thisWeek = weekSummary(
                workouts, gymVisits, fasts, watchWorkouts, observations,
                start = weekStart, end = weekEnd, zone = zone,
            ),
            lastWeek = weekSummary(
                workouts, gymVisits, fasts, watchWorkouts, observations,
                start = weekStart.minus(DatePeriod(days = 7)),
                end = weekStart.minus(DatePeriod(days = 1)),
                zone = zone,
            ),
            personalBests = personalBests(workouts, personalBestLimit),
            totalGymDays = attendanceDates(gymVisits).size,
        )
    }

    /**
     * Counts inside one local-date window. Workouts count on the day they
     * ended (the start day when no end is stored), fasts on the day they
     * ended, watch workouts on their start day and gym visits on their
     * recorded attendance date.
     */
    private fun weekSummary(
        workouts: List<WorkoutPayload>,
        gymVisits: List<GymVisit>,
        fasts: List<FastingSession>,
        watchWorkouts: List<WatchWorkoutPayload>,
        observations: List<HealthObservation>,
        start: LocalDate,
        end: LocalDate,
        zone: TimeZone,
    ): WeekSummary {
        val weekWorkouts = workouts.filter { entry ->
            entry.session.state == WorkoutState.COMPLETED &&
                entry.session.deletedUtc == null &&
                entry.localDate(zone)?.let { it in start..end } == true
        }
        val weekFasts = fasts.count {
            it.status == FastingSession.Status.COMPLETED &&
                it.deletedUtc == null &&
                it.actualEndInstant?.toLocalDateTime(zone)?.date
                    ?.let { d -> d in start..end } == true
        }
        val weekWatch = watchWorkouts.filter {
            Instant.fromEpochMilliseconds(it.startedUtc)
                .toLocalDateTime(zone).date in start..end
        }
        val stepRecords = observations.filter {
            it.marker in HealthSummary.CUMULATIVE_MARKERS &&
                it.deletedUtc == null &&
                it.measuredUtc.toLocalDateTime(zone).date in start..end
        }
        return WeekSummary(
            workouts = weekWorkouts.size,
            sets = weekWorkouts.sumOf { it.sets.size },
            gymDays = attendanceDates(gymVisits).count { it in start..end },
            completedFasts = weekFasts,
            watchWorkouts = weekWatch.size,
            watchDurationSec = weekWatch.sumOf { it.durationSec },
            steps = if (stepRecords.isEmpty()) null else stepRecords.sumOf { it.value },
        )
    }

    /**
     * Per-exercise heaviest set across completed sessions: the set with the
     * greatest load, then the greatest reps at that load. Sets without a
     * positive rep count or without a logged load never qualify.
     */
    private fun personalBests(
        workouts: List<WorkoutPayload>,
        limit: Int,
    ): List<PersonalBest> = workouts
        .filter { it.session.state == WorkoutState.COMPLETED && it.session.deletedUtc == null }
        .flatMap { it.sets }
        .mapNotNull { s ->
            val reps = s.reps ?: return@mapNotNull null
            val load = s.loadKg ?: return@mapNotNull null
            if (reps <= 0) null else PersonalBest(s.exerciseId, load, reps)
        }
        .groupBy { it.exerciseId }
        .mapNotNull { (_, candidates) ->
            candidates.maxWithOrNull(compareBy({ it.loadKg }, { it.reps }))
        }
        .sortedWith(compareByDescending<PersonalBest> { it.loadKg }.thenBy { it.exerciseId })
        .take(limit)

    /** Distinct local attendance dates among non-deleted visits. */
    private fun attendanceDates(visits: List<GymVisit>): Set<LocalDate> = visits
        .filter { it.deletedUtc == null }
        .mapNotNull { runCatching { LocalDate.parse(it.localAttendanceDate) }.getOrNull() }
        .toSet()

    private fun WorkoutPayload.localDate(zone: TimeZone): LocalDate? =
        (session.endedUtc ?: session.startedUtc)?.toLocalDateTime(zone)?.date
}
