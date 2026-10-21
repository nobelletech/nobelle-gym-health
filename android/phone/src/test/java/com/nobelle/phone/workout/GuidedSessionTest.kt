package com.nobelle.phone.workout

import com.nobelle.core.model.ExerciseSet
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSessionTest {
    private fun slot(
        index: Int,
        exercise: String = "push-up",
        rest: Int? = 90,
        logged: Boolean = false,
        id: String = "set-$index",
    ) = ExerciseSet(
        id = id,
        sessionId = "s1",
        exerciseId = exercise,
        setIndex = index,
        restSec = rest,
        completedUtc = if (logged) Instant.fromEpochMilliseconds(1_700_000_000_000) else null,
    )

    private val sets = listOf(
        slot(0, "push-up", logged = true),
        slot(1, "push-up"),
        slot(2, "push-up"),
        slot(3, "plank", rest = 45),
        slot(4, "plank", rest = 45),
    )

    @Test
    fun openSlotsAreDetectedByMissingCompletedUtc() {
        assertTrue(GuidedSession.isOpen(sets[1]))
        assertFalse(GuidedSession.isOpen(sets[0]))
        assertTrue(GuidedSession.hasOpenSlots(sets))
        assertFalse(
            GuidedSession.hasOpenSlots(sets.map { it.copy(completedUtc = Instant.DISTANT_PAST) }),
        )
    }

    @Test
    fun setNumberCountsWithinCurrentExerciseOnly() {
        assertEquals(1, GuidedSession.setNumberWithinExercise(sets, sets[0]))
        assertEquals(3, GuidedSession.setNumberWithinExercise(sets, sets[2]))
        assertEquals(1, GuidedSession.setNumberWithinExercise(sets, sets[3]))
        assertEquals(2, GuidedSession.setNumberWithinExercise(sets, sets[4]))
    }

    @Test
    fun nextOpenSlotSkipsLoggedSlotsAndUnknownIds() {
        // Logging set 0: set 1 is the next open slot even though set 0 is
        // already logged in this fixture.
        assertEquals("set-1", GuidedSession.nextOpenSlot(sets, "set-0")?.id)
        // Logging the last slot leaves nothing.
        assertNull(GuidedSession.nextOpenSlot(sets, "set-4"))
        // Unknown id yields no cue rather than guessing.
        assertNull(GuidedSession.nextOpenSlot(sets, "missing"))
    }

    @Test
    fun restCueComesFromTheNextOpenSlotsOwnRest() {
        assertEquals(90, GuidedSession.restCueAfter(sets, "set-0"))
        assertEquals(45, GuidedSession.restCueAfter(sets, "set-2"))
        assertNull(GuidedSession.restCueAfter(sets, "set-4"))

        // A next slot without a rest target produces no timer.
        val noRest = listOf(slot(0, "a"), slot(1, "b", rest = null))
        assertNull(GuidedSession.restCueAfter(noRest, "set-0"))
    }

    @Test
    fun remainingSecondsRoundsUpAndNeverGoesNegative() {
        val deadline = 90_000L
        assertEquals(90, GuidedSession.remainingSeconds(deadline, 0))
        assertEquals(90, GuidedSession.remainingSeconds(deadline, 1))
        assertEquals(89, GuidedSession.remainingSeconds(deadline, 1_000))
        assertEquals(1, GuidedSession.remainingSeconds(deadline, 89_001))
        assertEquals(0, GuidedSession.remainingSeconds(deadline, 90_000))
        assertEquals(0, GuidedSession.remainingSeconds(deadline, 120_000))
    }
}
