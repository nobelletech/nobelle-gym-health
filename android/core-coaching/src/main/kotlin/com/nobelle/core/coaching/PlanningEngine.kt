package com.nobelle.core.coaching

import com.nobelle.core.model.ExperienceLevel
import com.nobelle.core.model.Goal
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TrainingPlan
import com.nobelle.core.model.UserProfile
import java.util.UUID

/**
 * Deterministic, editable weekly plan generator (§5).
 * Rules are transparent and user-overridable; load is never auto-increased
 * from inferred reps or estimated recovery.
 */
class PlanningEngine(private val rulesVersion: String = "rules-v1") {

    /** Preview a suggested week; user edits fully before adoption (§4.8). */
    fun suggestWeek(profile: UserProfile, weekStartDate: String): TrainingPlan {
        val weekStart = java.time.LocalDate.parse(weekStartDate) // Monday
        val days = profile.preferredDays.ifEmpty { defaultDays(profile.experience) }.sorted()
        val categories = categoriesFor(profile.goals)
        val sessions = days.mapIndexed { i, dow ->
            PlannedSession(
                id = UUID.randomUUID().toString(),
                ownerUID = profile.ownerUID,
                localDate = weekStart.plusDays((dow - 1).toLong()).toString(),
                templateId = null,
                category = categories[i % categories.size],
            )
        }
        return TrainingPlan(
            id = UUID.randomUUID().toString(),
            ownerUID = profile.ownerUID,
            weekStartDate = weekStartDate,
            plannedSessions = sessions,
            generatedBy = rulesVersion,
        )
    }

    private fun defaultDays(level: ExperienceLevel) = when (level) {
        ExperienceLevel.BEGINNER -> setOf(1, 3, 5)
        ExperienceLevel.INTERMEDIATE -> setOf(1, 2, 4, 6)
        ExperienceLevel.ADVANCED -> setOf(1, 2, 3, 5, 6)
    }

    private fun categoriesFor(goals: Set<Goal>): List<String> {
        if (goals.isEmpty()) return listOf("full_body", "low_impact_cardio", "mobility")
        val cats = mutableListOf<String>()
        if (Goal.STRENGTH in goals || Goal.MUSCLE in goals) cats += listOf("upper_body", "lower_body")
        if (Goal.ENDURANCE in goals) cats += listOf("run_walk", "cardio")
        if (Goal.MOBILITY in goals) cats += "mobility"
        if (Goal.WEIGHT_MANAGEMENT in goals || Goal.GENERAL_FITNESS in goals) cats += listOf("full_body", "low_impact_cardio")
        return cats.ifEmpty { listOf("full_body") }
    }
}
