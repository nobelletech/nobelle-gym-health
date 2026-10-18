package com.nobelle.phone.workout

import com.nobelle.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEntryParserTest {
    @Test
    fun `blank optional fields become null and valid values parse`() {
        val result = SetEntryParser.parse(
            exerciseId = "  squat  ", reps = "5", loadKg = "82.5", durationSec = "",
            restSec = "120", rpe = "8.5", rir = "", tempo = " 3-0-1 ",
            painFlag = false, notes = " felt strong ", setType = SetType.WARM_UP,
            groupId = "", substitutedForExerciseId = "goblet squat",
        )
        val ok = result as SetEntryParser.Result.Ok
        assertEquals("squat", ok.entry.exerciseId)
        assertEquals(5, ok.entry.reps)
        assertEquals(82.5, ok.entry.loadKg!!, 0.0001)
        assertNull(ok.entry.durationSec)
        assertEquals(120, ok.entry.restSec)
        assertEquals(8.5, ok.entry.rpe!!, 0.0001)
        assertNull(ok.entry.rir)
        assertEquals("3-0-1", ok.entry.tempo)
        assertEquals("felt strong", ok.entry.notes)
        assertNull(ok.entry.groupId)
        assertEquals("goblet squat", ok.entry.substitutedForExerciseId)
        assertEquals(SetType.WARM_UP, ok.entry.setType)
    }

    @Test
    fun `rejects blank exercise`() {
        val result = SetEntryParser.parse(
            exerciseId = "   ", reps = "", loadKg = "", durationSec = "", restSec = "",
            rpe = "", rir = "", tempo = "", painFlag = false, notes = "",
        )
        assertTrue(result is SetEntryParser.Result.Invalid)
    }

    @Test
    fun `rejects non numeric input`() {
        val result = SetEntryParser.parse(
            exerciseId = "squat", reps = "five", loadKg = "", durationSec = "", restSec = "",
            rpe = "", rir = "", tempo = "", painFlag = false, notes = "",
        )
        assertTrue(result is SetEntryParser.Result.Invalid)
    }

    @Test
    fun `rejects out of range values`() {
        val negative = SetEntryParser.parse(
            exerciseId = "squat", reps = "-2", loadKg = "", durationSec = "", restSec = "",
            rpe = "", rir = "", tempo = "", painFlag = false, notes = "",
        )
        assertTrue(negative is SetEntryParser.Result.Invalid)
        val rpeTooHigh = SetEntryParser.parse(
            exerciseId = "squat", reps = "", loadKg = "", durationSec = "", restSec = "",
            rpe = "11", rir = "", tempo = "", painFlag = false, notes = "",
        )
        assertTrue(rpeTooHigh is SetEntryParser.Result.Invalid)
    }
}
