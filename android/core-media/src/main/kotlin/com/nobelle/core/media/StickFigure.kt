package com.nobelle.core.media

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure, JVM-testable stick-figure pose math for the schematic exercise
 * animations. No Android framework types beyond [Offset] (a plain value
 * class), no randomness, no clock — everything is a deterministic function
 * of the pose.
 *
 * Lives in core-media (not the phone module) so the phone and the watch
 * render the exact same figure from the same math, and so the math is
 * testable on the JVM without a device.
 *
 * Coordinate conventions
 *  - Unit space: the figure is laid out inside a 0..1 square, y-down.
 *  - The figure faces +x; a base transform rotates the whole figure for
 *    supine (lying on the back, head left) and prone (face down, head right)
 *    positions.
 *  - Angles are degrees. `torsoAngle` 0 is upright; positive leans forward.
 *    Shoulder/hip flex 0 keeps the limb hanging along the torso line,
 *    positive brings it forward/up (180 = overhead). Elbow/knee flex 0 is a
 *    straight limb; positive folds the forearm/shin backward.
 *  - Direction vector: v(a) = (sin a, -cos a): 0 = up, 90 = forward,
 *    180 = down, 270 = backward.
 *
 * The joints list order is:
 * [0] head centre, [1] neck (also the shoulder anchor), [2] hip,
 * [3] shoulder, [4] elbow, [5] wrist, [6] knee, [7] ankle.
 */
data class Pose(
    val torsoAngleDeg: Float,
    val shoulderFlexDeg: Float,
    val elbowFlexDeg: Float,
    val hipFlexDeg: Float,
    val kneeFlexDeg: Float,
    val base: PoseBase,
    val prop: PoseProp,
) {
    enum class PoseBase { STANDING, SUPINE, SEATED, KNEELING, QUADRUPED, FOREARM_PLANK }

    /** Changes only the drawn overlay in the renderer, never the joint math. */
    enum class PoseProp { NONE, BARBELL, DUMBBELL, BAND, CABLE, BODYWEIGHT }
}

/** A neutral, upright standing pose used when no primitive matches. */
val NEUTRAL_POSE = Pose(
    torsoAngleDeg = 0f, shoulderFlexDeg = 10f, elbowFlexDeg = 10f,
    hipFlexDeg = 0f, kneeFlexDeg = 0f,
    base = Pose.PoseBase.STANDING, prop = Pose.PoseProp.NONE,
)

object StickFigurePose {
    const val HEAD = 0
    const val NECK = 1
    const val HIP = 2
    const val SHOULDER = 3
    const val ELBOW = 4
    const val WRIST = 5
    const val KNEE = 6
    const val ANKLE = 7

    // Segment lengths in unit space, chosen so every primitive's figure fits
    // inside the 0..1 square with its ground contact on the ground line.
    const val TORSO = 0.26f      // hip -> neck
    const val NECK_LEN = 0.06f   // neck -> head centre
    const val UPPER_ARM = 0.13f
    const val FOREARM = 0.12f
    const val THIGH = 0.19f
    const val SHIN = 0.17f

    // Ground line height in unit space. Supine figures sit higher because
    // bent shins extend below the torso line (feet on the floor).
    private const val GROUND_STANDING = 0.86f
    private const val GROUND_SUPINE = 0.70f

    private fun v(angleDeg: Float): Offset {
        val r = angleDeg * (PI / 180f)
        return Offset(sin(r), -cos(r))
    }

    private fun rotate(point: Offset, base: Pose.PoseBase): Offset = when (base) {
        Pose.PoseBase.STANDING, Pose.PoseBase.SEATED, Pose.PoseBase.KNEELING -> point
        // Figure rotated 90° counter-clockwise: head left, face up, back on ground.
        Pose.PoseBase.SUPINE -> Offset(point.y, -point.x)
        // Figure rotated 90° clockwise: head right, face down.
        Pose.PoseBase.QUADRUPED, Pose.PoseBase.FOREARM_PLANK -> Offset(-point.y, point.x)
    }

    private fun anchorsOf(base: Pose.PoseBase): List<Int> = when (base) {
        Pose.PoseBase.STANDING, Pose.PoseBase.SEATED -> listOf(ANKLE)
        Pose.PoseBase.KNEELING -> listOf(KNEE)
        Pose.PoseBase.SUPINE -> listOf(HIP)
        Pose.PoseBase.QUADRUPED -> listOf(WRIST, KNEE)
        Pose.PoseBase.FOREARM_PLANK -> listOf(WRIST, ANKLE)
    }

    private fun groundYOf(base: Pose.PoseBase): Float = when (base) {
        Pose.PoseBase.SUPINE -> GROUND_SUPINE
        else -> GROUND_STANDING
    }

    /**
     * Computes the eight joints of [pose] in unit space (0..1, y-down):
     * [head, neck, hip, shoulder, elbow, wrist, knee, ankle]. The figure is
     * translated so its support joints rest on the ground line and centred
     * horizontally. Pure function of the pose — no animation math here.
     */
    fun joints(pose: Pose): List<Offset> {
        val theta = pose.torsoAngleDeg
        // Standing frame, hip at the origin.
        val neck = v(theta) * TORSO
        val shoulder = neck
        val head = neck + v(theta) * NECK_LEN

        val upperArmAngle = theta + 180f - pose.shoulderFlexDeg
        val elbow = shoulder + v(upperArmAngle) * UPPER_ARM
        val forearmAngle = upperArmAngle - pose.elbowFlexDeg
        val wrist = elbow + v(forearmAngle) * FOREARM

        val thighAngle = theta + 180f - pose.hipFlexDeg
        val knee = v(thighAngle) * THIGH
        val shinAngle = thighAngle + pose.kneeFlexDeg
        val ankle = knee + v(shinAngle) * SHIN

        val hip = Offset.Zero
        val raw = listOf(head, neck, hip, shoulder, elbow, wrist, knee, ankle)

        val rotated = raw.map { rotate(it, pose.base) }

        // Drop the support joints onto the ground line, then centre the
        // bounding box horizontally. Translation only — segments keep their
        // exact lengths, so limb proportions never stretch.
        val groundY = groundYOf(pose.base)
        val anchors = anchorsOf(pose.base)
        val shiftY = groundY - anchors.maxOf { rotated[it].y }
        val minX = rotated.minOf { it.x }
        val maxX = rotated.maxOf { it.x }
        val shiftX = 0.5f - (minX + maxX) / 2f
        return rotated.map { Offset(it.x + shiftX, it.y + shiftY) }
    }

    /** The y coordinate (unit space) of the ground line under [pose]. */
    fun groundLineY(pose: Pose): Float {
        val joints = joints(pose)
        return anchorsOf(pose.base).maxOf { joints[it].y }
    }

    private const val PI = 3.141592653589793f
}

/**
 * The 16 canonical motion primitives (see ExerciseCatalog.MOTION_PRIMITIVES)
 * as looping, deterministic phase functions. Each primitive is 2-3 keyframes
 * blended with a smoothstep ease between consecutive keyframes; the last
 * keyframe repeats the first so the loop point is continuous (burpee is a
 * chained primitive and intentionally cuts hard back to its first keyframe).
 *
 * Keyframes are authored in the standing frame; the pose base applies the
 * orientation transform. All angles in degrees.
 */
object MotionPrimitives {
    private class Keyframe(val at: Float, val pose: Pose)

    private fun kf(
        at: Float, torso: Float, shoulder: Float, elbow: Float,
        hip: Float, knee: Float, base: Pose.PoseBase, prop: Pose.PoseProp,
    ) = Keyframe(at, Pose(torso, shoulder, elbow, hip, knee, base, prop))

    private val primitives: Map<String, List<Keyframe>> = mapOf(
        // Bench-press-style: elbows flexed at the chest, extend up, return.
        "press" to listOf(
            kf(0f, 0f, 90f, 70f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
            kf(0.5f, 0f, 90f, 5f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
            kf(1f, 0f, 90f, 70f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
        ),
        // Prone push-up: straight arms, elbows fold, body lowers.
        "push_up" to listOf(
            kf(0f, 0f, 151f, 0f, 0f, 0f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(0.5f, 0f, -39f, 153f, 0f, 0f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(1f, 0f, 151f, 0f, 0f, 0f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
        ),
        // Squat: stand, sit hips down to about parallel, stand.
        "squat" to listOf(
            kf(0f, 10f, 90f, 10f, 10f, 10f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(0.5f, 40f, 90f, 10f, 95f, 95f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(1f, 10f, 90f, 10f, 10f, 10f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
        ),
        // Lunge: step forward and down, return.
        "lunge" to listOf(
            kf(0f, 0f, 15f, 10f, 5f, 5f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(0.5f, 8f, 15f, 10f, 75f, 85f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(1f, 0f, 15f, 10f, 5f, 5f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
        ),
        // Hip hinge: torso folds toward horizontal with soft knees, return.
        "hinge" to listOf(
            kf(0f, 0f, 0f, 5f, 10f, 10f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(0.5f, 75f, 0f, 5f, 80f, 30f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(1f, 0f, 0f, 5f, 10f, 10f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
        ),
        // Hip thrust: shoulders on the bench, hips drop then drive to level.
        "hip_thrust" to listOf(
            kf(0f, -20f, 180f, 10f, -60f, 0f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
            kf(0.5f, 0f, 180f, 10f, -27f, 63f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
            kf(1f, -20f, 180f, 10f, -60f, 0f, Pose.PoseBase.SUPINE, Pose.PoseProp.BARBELL),
        ),
        // Bent-over row: hinged torso, arms hang, pull elbows back to the ribs.
        "row" to listOf(
            kf(0f, 55f, 0f, 5f, 60f, 15f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(0.5f, 55f, -75f, -135f, 60f, 15f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
            kf(1f, 55f, 0f, 5f, 60f, 15f, Pose.PoseBase.STANDING, Pose.PoseProp.BARBELL),
        ),
        // Seated pulldown: arms overhead, pull the bar to the upper chest.
        "pulldown" to listOf(
            kf(0f, 12f, 180f, 5f, 90f, 90f, Pose.PoseBase.SEATED, Pose.PoseProp.CABLE),
            kf(0.5f, 12f, 30f, 90f, 90f, 90f, Pose.PoseBase.SEATED, Pose.PoseProp.CABLE),
            kf(1f, 12f, 180f, 5f, 90f, 90f, Pose.PoseBase.SEATED, Pose.PoseProp.CABLE),
        ),
        // Curl: elbow pinned, forearm folds up to the shoulder, return.
        "curl" to listOf(
            kf(0f, 0f, 0f, 5f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(0.5f, 0f, 0f, 140f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(1f, 0f, 0f, 5f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
        ),
        // Raise: arms out to the sides up to shoulder height, return.
        "raise" to listOf(
            kf(0f, 0f, 5f, 10f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(0.5f, 0f, 90f, 15f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(1f, 0f, 5f, 10f, 0f, 0f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
        ),
        // Fly: lying, arms open toward the floor then come together overhead.
        "fly" to listOf(
            kf(0f, 0f, 90f, -30f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.DUMBBELL),
            kf(0.5f, 0f, 90f, 5f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.DUMBBELL),
            kf(1f, 0f, 90f, -30f, 0f, 90f, Pose.PoseBase.SUPINE, Pose.PoseProp.DUMBBELL),
        ),
        // Plank: static forearm plank hold.
        "plank" to listOf(
            kf(0f, 0f, 90f, 90f, 0f, 0f, Pose.PoseBase.FOREARM_PLANK, Pose.PoseProp.BODYWEIGHT),
            kf(0.5f, 0f, 90f, 90f, 0f, 0f, Pose.PoseBase.FOREARM_PLANK, Pose.PoseProp.BODYWEIGHT),
            kf(1f, 0f, 90f, 90f, 0f, 0f, Pose.PoseBase.FOREARM_PLANK, Pose.PoseProp.BODYWEIGHT),
        ),
        // Crunch: lying, knees up, curl the shoulders off the floor.
        "crunch" to listOf(
            kf(0f, 0f, 180f, 5f, 90f, 180f, Pose.PoseBase.SUPINE, Pose.PoseProp.BODYWEIGHT),
            kf(0.5f, 35f, 180f, 5f, 90f, 180f, Pose.PoseBase.SUPINE, Pose.PoseProp.BODYWEIGHT),
            kf(1f, 0f, 180f, 5f, 90f, 180f, Pose.PoseBase.SUPINE, Pose.PoseProp.BODYWEIGHT),
        ),
        // Carry: steady walk stride with arms hanging at the sides.
        "carry" to listOf(
            kf(0f, 5f, 5f, 15f, 15f, 20f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(0.5f, 5f, 8f, 15f, 25f, 15f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
            kf(1f, 5f, 5f, 15f, 15f, 20f, Pose.PoseBase.STANDING, Pose.PoseProp.DUMBBELL),
        ),
        // Climb: quadruped, drive one knee up under the chest, return.
        "climb" to listOf(
            kf(0f, 0f, 151f, 0f, 90f, 90f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(0.5f, 0f, 151f, 0f, -45f, -135f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(1f, 0f, 151f, 0f, 90f, 90f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
        ),
        // Burpee: plank -> knee tuck -> stand. Chain primitive: the loop
        // cuts hard from the standing pose back to the plank (acceptable).
        "burpee" to listOf(
            kf(0f, 0f, 151f, 0f, 90f, 90f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(0.5f, 0f, 151f, 0f, -45f, -135f, Pose.PoseBase.QUADRUPED, Pose.PoseProp.BODYWEIGHT),
            kf(1f, 0f, 90f, 10f, 15f, 20f, Pose.PoseBase.STANDING, Pose.PoseProp.BODYWEIGHT),
        ),
    )

    private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)

    private fun lerp(a: Float, b: Float, u: Float): Float = a + (b - a) * u

    /** True when [primitive] is one of the canonical motion primitives. */
    fun known(primitive: String): Boolean = primitive in primitives

    /** The canonical set this animator can draw (mirrors the catalog set). */
    fun supported(): Set<String> = primitives.keys

    /**
     * Returns the pose for phase [t] in 0..1 of [primitive]. Loops forever,
     * fully deterministic. Unknown primitives throw [IllegalArgumentException]
     * — callers must filter against the canonical set first.
     */
    fun phase(primitive: String, t: Float): Pose {
        val keyframes = primitives[primitive]
            ?: throw IllegalArgumentException("Unknown motion primitive: $primitive")
        val wrapped = ((t % 1f) + 1f) % 1f
        var segment = 0
        while (segment < keyframes.size - 2 && wrapped > keyframes[segment + 1].at) segment++
        val from = keyframes[segment]
        val to = keyframes[segment + 1]
        val local = ((wrapped - from.at) / (to.at - from.at)).coerceIn(0f, 1f)
        val u = smoothstep(local)
        val a = from.pose
        val b = to.pose
        return Pose(
            torsoAngleDeg = lerp(a.torsoAngleDeg, b.torsoAngleDeg, u),
            shoulderFlexDeg = lerp(a.shoulderFlexDeg, b.shoulderFlexDeg, u),
            elbowFlexDeg = lerp(a.elbowFlexDeg, b.elbowFlexDeg, u),
            hipFlexDeg = lerp(a.hipFlexDeg, b.hipFlexDeg, u),
            kneeFlexDeg = lerp(a.kneeFlexDeg, b.kneeFlexDeg, u),
            // Orientation flips at the halfway point of a base-changing
            // segment (only burpee uses two bases).
            base = if (local < 0.5f) a.base else b.base,
            prop = a.prop,
        )
    }

    /**
     * Divides the 0..1 phase into one window per narration/instruction step,
     * in instruction order: setup steps come first, then the primary
     * movement, then the return to the start position.
     *
     * Mapping (deterministic, same for every primitive):
     *  - 1 step  -> the whole 0..1 loop.
     *  - 2 steps -> movement [0, 0.5], return [0.5, 1].
     *  - N >= 3  -> the first N-2 steps (setup) share [0, 0.2] evenly,
     *    the movement step is [0.2, 0.55], the return step is [0.55, 1].
     *
     * Invariants (unit-tested): windows are returned in step order, are
     * monotonic and non-overlapping, and together cover exactly 0..1. Used
     * to sync the figure with narration: while step i plays, the figure
     * loops inside window i.
     */
    fun stepWindows(primitive: String, stepCount: Int): List<Pair<Float, Float>> {
        require(primitive in primitives) { "Unknown motion primitive: $primitive" }
        require(stepCount >= 1) { "stepCount must be >= 1, was $stepCount" }
        return when (stepCount) {
            1 -> listOf(0f to 1f)
            2 -> listOf(0f to 0.5f, 0.5f to 1f)
            else -> {
                val setupSteps = stepCount - 2
                val setupEnd = 0.2f
                val movementEnd = 0.55f
                buildList {
                    for (i in 0 until setupSteps) {
                        add(setupEnd * i / setupSteps to setupEnd * (i + 1) / setupSteps)
                    }
                    add(setupEnd to movementEnd)
                    add(movementEnd to 1f)
                }
            }
        }
    }
}