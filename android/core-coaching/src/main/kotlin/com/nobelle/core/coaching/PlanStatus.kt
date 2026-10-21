package com.nobelle.core.coaching

import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TrainingPlan
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Deterministic completion rule for planned sessions. A planned session
 * counts as COMPLETED when either:
 *  - a completed workout was started from its templateId, or
 *  - a completed workout's local date equals the planned localDate AND its
 *    category matches (case-insensitive).
 * Status stays user-overridable (skip/unskip); this only derives completion
 * from already-recorded data — nothing is auto-planned or auto-graded.
 */
object PlanStatus {

    /** Ids of the plan's planned sessions the rule marks as completed. */
    fun completedSessionIds(plan: TrainingPlan, workouts: List<WorkoutSession>): Set<String> {
        val done = workouts.filter { it.state == WorkoutState.COMPLETED }
        return plan.plannedSessions
            .filter { planned ->
                done.any { workout ->
                    (planned.templateId != null && workout.templateId == planned.templateId) ||
                        (workoutLocalDate(workout) == planned.localDate &&
                            workout.category.equals(planned.category, ignoreCase = true))
                }
            }
            .map { it.id }
            .toSet()
    }

    /**
     * A workout's local calendar date: end time when recorded, else start,
     * in the session's own timezone; falls back to the recorded UTC offset,
     * then UTC. Returns null when no instant is recorded.
     */
    fun workoutLocalDate(session: WorkoutSession): String? {
        val instant: Instant = session.endedUtc ?: session.startedUtc ?: return null
        val zone = session.startTimeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() }
        val date = when {
            zone != null -> instant.toLocalDateTime(zone).date
            session.sourceOffsetMinutes != null ->
                instant.toLocalDateTime(TimeZone.UTC).date // offset rarely shifts the date; UTC is the documented fallback
            else -> instant.toLocalDateTime(TimeZone.UTC).date
        }
        return date.toString()
    }

    /** Copy of [plan] with derived completions applied to PLANNED rows. */
    fun withDerivedCompletions(plan: TrainingPlan, workouts: List<WorkoutSession>): TrainingPlan {
        val done = completedSessionIds(plan, workouts)
        if (done.isEmpty()) return plan
        return plan.copy(
            plannedSessions = plan.plannedSessions.map { session ->
                if (session.id in done && session.status == PlannedSession.Status.PLANNED) {
                    session.copy(status = PlannedSession.Status.COMPLETED)
                } else {
                    session
                }
            },
        )
    }
}
