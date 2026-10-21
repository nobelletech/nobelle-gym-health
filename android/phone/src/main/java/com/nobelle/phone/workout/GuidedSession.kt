package com.nobelle.phone.workout

import com.nobelle.core.model.ExerciseSet

/** User-entered values for filling one open slot (never prefilled — §8C). */
data class SlotLogEntry(
    val setId: String,
    val reps: Int?,
    val loadKg: Double?,
    val durationSec: Int?,
    val rpe: Double?,
    val notes: String?,
)

/**
 * Pure helpers for the guided template session card. A "slot" is a prefilled
 * ExerciseSet row created by startFromTemplate: it is open until the user
 * enters values and it gains a completedUtc. Nothing here logs values —
 * it only reads the stored slots to decide what the UI should offer.
 */
object GuidedSession {

    /** A slot is open until the user logs it (completedUtc stays null). */
    fun isOpen(set: ExerciseSet): Boolean = set.completedUtc == null

    /** Slots in workout order; open ones first is NOT wanted — keep setIndex. */
    fun ordered(sets: List<ExerciseSet>): List<ExerciseSet> = sets.sortedBy { it.setIndex }

    /** True when the session has any unfilled slot worth guiding through. */
    fun hasOpenSlots(sets: List<ExerciseSet>): Boolean = sets.any(::isOpen)

    /**
     * The set number within its own exercise ("Bench · set 2 of 3"), counting
     * earlier slots that share the same exerciseId — regardless of swaps, the
     * current exerciseId is what groups a slot.
     */
    fun setNumberWithinExercise(sets: List<ExerciseSet>, slot: ExerciseSet): Int =
        ordered(sets)
            .filter { it.exerciseId == slot.exerciseId && it.setIndex <= slot.setIndex }
            .size

    /**
     * After [justLoggedSetId] is filled, the next open slot in setIndex order
     * — the one the user will rest for. Returns null when nothing remains.
     */
    fun nextOpenSlot(sets: List<ExerciseSet>, justLoggedSetId: String): ExerciseSet? {
        val justLogged = sets.firstOrNull { it.id == justLoggedSetId } ?: return null
        return ordered(sets).firstOrNull { it.setIndex > justLogged.setIndex && isOpen(it) }
    }

    /**
     * The rest cue after logging [justLoggedSetId]: uses the NEXT open slot's
     * own restSec (the recovery the template planned before that set). Null
     * when there is no next open slot or it carries no rest target.
     */
    fun restCueAfter(sets: List<ExerciseSet>, justLoggedSetId: String): Int? =
        nextOpenSlot(sets, justLoggedSetId)?.restSec?.takeIf { it > 0 }

    /** Whole seconds remaining until [deadlineMs]; never negative. */
    fun remainingSeconds(deadlineMs: Long, nowMs: Long): Long {
        val delta = deadlineMs - nowMs
        if (delta <= 0) return 0
        return (delta + 999) / 1000
    }
}
