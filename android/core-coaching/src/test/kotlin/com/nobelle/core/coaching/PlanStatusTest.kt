package com.nobelle.core.coaching

import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TrainingPlan
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanStatusTest {
    private val owner = "owner-a"
    private val plan = TrainingPlan(
        id = "p1", ownerUID = owner, weekStartDate = "2024-01-01", generatedBy = "rules-v1",
        plannedSessions = listOf(
            PlannedSession("ps1", owner, "2024-01-01", templateId = "t1", category = "upper_body"),
            PlannedSession("ps2", owner, "2024-01-03", templateId = null, category = "mobility"),
            PlannedSession("ps3", owner, "2024-01-05", templateId = null, category = "cardio",
                status = PlannedSession.Status.SKIPPED),
        ),
    )

    private fun workout(
        category: String,
        day: String,
        templateId: String? = null,
        state: WorkoutState = WorkoutState.COMPLETED,
    ) = WorkoutSession(
        id = "$category-$day-${templateId ?: "x"}", ownerUID = owner,
        category = category, state = state,
        templateId = templateId,
        startedUtc = Instant.parse("${day}T10:00:00Z"),
        endedUtc = Instant.parse("${day}T11:00:00Z"),
        startTimeZone = "UTC",
        originDeviceId = "dev",
    )

    @Test fun `template match and date plus category match mark sessions completed`() {
        val done = listOf(
            workout("anything", "2024-01-02", templateId = "t1"),
            workout("Mobility", "2024-01-03"),
        )
        assertEquals(setOf("ps1", "ps2"), PlanStatus.completedSessionIds(plan, done))
    }

    @Test fun `non-completed workouts and mismatched categories do not complete`() {
        val done = listOf(
            workout("mobility", "2024-01-03", state = WorkoutState.ACTIVE),
            workout("strength", "2024-01-03"),
        )
        assertTrue(PlanStatus.completedSessionIds(plan, done).isEmpty())
    }

    @Test fun `derived completions never unskip a skipped session`() {
        val done = listOf(workout("cardio", "2024-01-05"))
        val derived = PlanStatus.withDerivedCompletions(plan, done)
        assertEquals(PlannedSession.Status.SKIPPED,
            derived.plannedSessions.single { it.id == "ps3" }.status)
    }

    @Test fun `identical derivation returns the same plan`() {
        assertEquals(plan, PlanStatus.withDerivedCompletions(plan, emptyList()))
    }
}
