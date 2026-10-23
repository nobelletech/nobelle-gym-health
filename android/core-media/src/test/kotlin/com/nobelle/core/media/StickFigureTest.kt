package com.nobelle.core.media

import androidx.compose.ui.geometry.Offset
import com.nobelle.core.coaching.MOTION_PRIMITIVES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class StickFigureTest {

    @Test
    fun animatorCoversAllCanonicalPrimitivesOneToOne() {
        assertEquals(MOTION_PRIMITIVES, MotionPrimitives.supported())
        MOTION_PRIMITIVES.forEach { primitive ->
            assertTrue("$primitive must be known", MotionPrimitives.known(primitive))
        }
    }

    @Test
    fun jointsAreFiniteAcrossTheWholePhaseSweep() {
        for (primitive in MOTION_PRIMITIVES) {
            for (i in 0..20) {
                val t = i / 20f
                val joints = StickFigurePose.joints(MotionPrimitives.phase(primitive, t))
                assertEquals("$primitive: joint count", 8, joints.size)
                joints.forEach { joint ->
                    assertTrue("$primitive t=$t: x not finite", joint.x.isFinite())
                    assertTrue("$primitive t=$t: y not finite", joint.y.isFinite())
                }
            }
        }
    }

    @Test
    fun segmentLengthsStayConstantAcrossThePhaseSweep() {
        for (primitive in MOTION_PRIMITIVES) {
            for (i in 0..20) {
                val t = i / 20f
                val j = StickFigurePose.joints(MotionPrimitives.phase(primitive, t))
                assertDistance("$primitive t=$t torso", StickFigurePose.TORSO, j[NECK], j[HIP])
                assertDistance("$primitive t=$t neck", StickFigurePose.NECK_LEN, j[NECK], j[HEAD])
                assertDistance("$primitive t=$t upper arm", StickFigurePose.UPPER_ARM, j[SHOULDER], j[ELBOW])
                assertDistance("$primitive t=$t forearm", StickFigurePose.FOREARM, j[ELBOW], j[WRIST])
                assertDistance("$primitive t=$t thigh", StickFigurePose.THIGH, j[HIP], j[KNEE])
                assertDistance("$primitive t=$t shin", StickFigurePose.SHIN, j[KNEE], j[ANKLE])
            }
        }
    }

    @Test
    fun phaseZeroEqualsPhaseOneForLoopingPrimitives() {
        // Every primitive is authored with first keyframe == last keyframe so
        // the loop point is continuous — except burpee, which is a chained
        // primitive and intentionally cuts hard from standing back to the plank.
        // (phase(1) wraps to phase(0) by definition, so continuity is checked
        // as t approaches 1 from below.)
        MOTION_PRIMITIVES.filter { it != "burpee" }.forEach { primitive ->
            val atZero = StickFigurePose.joints(MotionPrimitives.phase(primitive, 0f))
            val atLoopPoint = StickFigurePose.joints(MotionPrimitives.phase(primitive, 0.999f))
            atZero.zip(atLoopPoint).forEachIndexed { index, (a, b) ->
                assertEquals("$primitive joint $index", 0f, (a - b).getDistance(), 1e-3f)
            }
        }
    }

    @Test
    fun burpeeLoopCutIsIntentionalAndDocumented() {
        // Guards the documented exception: burpee cuts from a standing pose
        // back to the quadruped plank at the loop point.
        val atZero = StickFigurePose.joints(MotionPrimitives.phase("burpee", 0f))
        val atLoopPoint = StickFigurePose.joints(MotionPrimitives.phase("burpee", 0.999f))
        val maxDelta = atZero.zip(atLoopPoint).maxOf { (a, b) -> (a - b).getDistance() }
        assertTrue("burpee must not be loop-continuous", maxDelta > 0.05f)
    }

    @Test
    fun phaseWrapsAroundOutsideTheUnitRange() {
        for (primitive in MOTION_PRIMITIVES) {
            val atQuarter = StickFigurePose.joints(MotionPrimitives.phase(primitive, 0.25f))
            val atOneAndQuarter = StickFigurePose.joints(MotionPrimitives.phase(primitive, 1.25f))
            atQuarter.zip(atOneAndQuarter).forEachIndexed { index, (a, b) ->
                assertEquals("$primitive joint $index", 0f, (a - b).getDistance(), 1e-4f)
            }
        }
    }

    @Test
    fun unknownPrimitiveThrows() {
        try {
            MotionPrimitives.phase("breakdancing", 0.5f)
            fail("unknown primitive must throw")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun stepWindowsCoverTheWholePhaseWithoutOverlap() {
        for (primitive in MOTION_PRIMITIVES) {
            for (stepCount in 1..8) {
                val windows = MotionPrimitives.stepWindows(primitive, stepCount)
                assertEquals("$primitive/$stepCount window count", stepCount, windows.size)
                assertEquals("$primitive/$stepCount first start", 0f, windows.first().first, 1e-6f)
                assertEquals("$primitive/$stepCount last end", 1f, windows.last().second, 1e-6f)
                windows.forEachIndexed { index, (start, end) ->
                    assertTrue("$primitive/$stepCount w$index start", start in 0f..1f)
                    assertTrue("$primitive/$stepCount w$index end", end in 0f..1f)
                    assertTrue("$primitive/$stepCount w$index empty/inverted", start < end)
                }
                // Monotonic and non-overlapping (adjacent windows may only
                // touch at their shared boundary).
                windows.zipWithNext().forEach { (a, b) ->
                    assertTrue(
                        "$primitive/$stepCount overlap or out of order: $a -> $b",
                        a.second <= b.first,
                    )
                }
            }
        }
    }

    @Test
    fun stepWindowsFollowSetupMovementReturnOrder() {
        // The first window must begin at 0 (setup), the last must end at 1
        // (return), and with 3+ steps the setup windows precede the movement
        // window which precedes the return window. This is the mapping the
        // narration sync relies on.
        for (primitive in MOTION_PRIMITIVES) {
            val windows = MotionPrimitives.stepWindows(primitive, 4)
            assertEquals(4, windows.size)
            assertEquals(0f, windows[0].first, 1e-6f)       // setup starts at the loop start
            assertTrue(windows[0].second <= windows[1].first) // setup 1 before setup 2
            assertTrue(windows[1].second <= windows[2].first) // setup before movement
            assertTrue(windows[2].second <= windows[3].first) // movement before return
            assertEquals(1f, windows[3].second, 1e-6f)        // return ends the loop
        }
    }

    @Test
    fun neutralPoseJointsAreFinite() {
        StickFigurePose.joints(NEUTRAL_POSE).forEach { joint ->
            assertTrue(joint.x.isFinite())
            assertTrue(joint.y.isFinite())
        }
    }

    @Test
    fun staticPoseIsDeterministic() {
        val a = StickFigurePose.joints(MotionPrimitives.phase("squat", 0.3f))
        val b = StickFigurePose.joints(MotionPrimitives.phase("squat", 0.3f))
        a.zip(b).forEachIndexed { index, (x, y) ->
            assertEquals(index.toString(), 0f, (x - y).getDistance(), 0f)
        }
    }

    private fun assertDistance(message: String, expected: Float, a: Offset, b: Offset) {
        assertEquals(message, expected, (a - b).getDistance(), 1e-3f)
    }

    private companion object {
        const val NECK = StickFigurePose.NECK
        const val HIP = StickFigurePose.HIP
        const val SHOULDER = StickFigurePose.SHOULDER
        const val ELBOW = StickFigurePose.ELBOW
        const val WRIST = StickFigurePose.WRIST
        const val KNEE = StickFigurePose.KNEE
        const val ANKLE = StickFigurePose.ANKLE
        const val HEAD = StickFigurePose.HEAD
    }
}