package com.nobelle.core.coaching

import com.nobelle.core.model.GymVisit

/**
 * Attendance accounting (§20): unique gym days vs total visits;
 * unconfirmed suggestions and future plans never count.
 */
object Attendance {

    fun uniqueGymDays(visits: List<GymVisit>): Int =
        visits.filter { it.confirmed && it.deletedUtc == null }
            .map { it.localAttendanceDate }.toSet().size

    fun totalVisits(visits: List<GymVisit>): Int =
        visits.count { it.confirmed && it.deletedUtc == null }

    /** Multiple workouts linked to one visit must not inflate attendance. */
    fun linkWorkout(visit: GymVisit, workoutId: String): GymVisit =
        if (workoutId in visit.workoutIds) visit
        else visit.copy(workoutIds = visit.workoutIds + workoutId, revision = visit.revision + 1)
}
