package com.nobelle.core.media

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nobelle.core.coaching.MOTION_PRIMITIVES
import kotlin.math.min

/**
 * Shared schematic stick-figure renderer used by the phone (exercise detail,
 * browse thumbnails) and the watch (exercise preview). Pure drawing on top of
 * [StickFigurePose]/[MotionPrimitives] — a schematic, not a video, and never
 * presented as a substitute for the written instructions.
 *
 * - [animated]: when true the figure loops the phase; when false a static
 *   pose at the start of the loop is drawn (browse thumbnails).
 * - [loopMillis]: one full 0..1 sweep duration (~2.4 s by default).
 * - [phaseWindow]: optional sub-window of the phase to loop inside; used to
 *   sync the figure with the currently narrated instruction step. The
 *   animation never speaks — narration is driven separately.
 * - Unknown motion values fall back to a static neutral pose, never a crash.
 */
@Composable
fun ExerciseFigure(
    primitive: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    loopMillis: Long = 2400L,
    phaseWindow: Pair<Float, Float>? = null,
    figureColor: Color? = null,
    propColor: Color? = null,
) {
    val known = primitive in MOTION_PRIMITIVES
    val figure = figureColor ?: LocalContentColor.current
    val prop = propColor ?: figure

    var phase by remember { mutableFloatStateOf(phaseWindow?.first ?: 0f) }

    if (animated && known) {
        LaunchedEffect(phaseWindow, loopMillis) {
            val start = phaseWindow?.first ?: 0f
            val end = phaseWindow?.second ?: 1f
            val span = end - start
            while (true) {
                androidx.compose.runtime.withFrameNanos { nanos ->
                    val t = (nanos % (loopMillis * 1_000_000L)).toFloat() / (loopMillis * 1_000_000L)
                    phase = start + span * t
                }
            }
        }
    }

    val pose = if (known) MotionPrimitives.phase(primitive, phase) else NEUTRAL_POSE
    val joints = StickFigurePose.joints(pose)
    val groundY = StickFigurePose.groundLineY(pose)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = min(w, h)
        fun toUnit(o: Offset) = Offset(o.x * w, o.y * h)

        val stroke = Stroke(width = (s * 0.014f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        val jointRadius = (s * 0.016f).coerceAtLeast(1.2f)

        // Ground line.
        drawLine(
            color = figure.copy(alpha = 0.35f),
            start = Offset(0f, groundY * h),
            end = Offset(w, groundY * h),
            strokeWidth = stroke.width,
        )

        fun limb(a: Int, b: Int) {
            drawLine(figure, toUnit(joints[a]), toUnit(joints[b]), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        limb(StickFigurePose.NECK, StickFigurePose.HIP)      // torso
        limb(StickFigurePose.NECK, StickFigurePose.SHOULDER) // shoulder anchor
        limb(StickFigurePose.SHOULDER, StickFigurePose.ELBOW)
        limb(StickFigurePose.ELBOW, StickFigurePose.WRIST)
        limb(StickFigurePose.HIP, StickFigurePose.KNEE)
        limb(StickFigurePose.KNEE, StickFigurePose.ANKLE)
        limb(StickFigurePose.NECK, StickFigurePose.HEAD)

        // Head.
        drawCircle(
            color = figure,
            radius = s * StickFigurePose.NECK_LEN * 0.55f,
            center = toUnit(joints[StickFigurePose.HEAD]),
        )

        // Joint dots (all but the head centre).
        for (i in 1 until joints.size) {
            drawCircle(figure, jointRadius, toUnit(joints[i]))
        }

        // Prop overlays — decorative only, they never change the pose math.
        val wrist = joints[StickFigurePose.WRIST]
        val elbow = joints[StickFigurePose.ELBOW]
        when (pose.prop) {
            Pose.PoseProp.BARBELL, Pose.PoseProp.DUMBBELL -> {
                val dir = wrist - elbow
                val length = dir.getDistance().takeIf { it > 0f } ?: 1f
                val perp = Offset(-dir.y / length, dir.x / length)
                val half = if (pose.prop == Pose.PoseProp.BARBELL) 0.075f else 0.04f
                val a = wrist - perp * (half * s)
                val b = wrist + perp * (half * s)
                drawLine(prop, toUnit(a), toUnit(b), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
                val plate = if (pose.prop == Pose.PoseProp.BARBELL) s * 0.022f else s * 0.012f
                drawCircle(prop, plate, toUnit(a))
                drawCircle(prop, plate, toUnit(b))
            }
            Pose.PoseProp.CABLE -> {
                val top = Offset(wrist.x, 0f)
                drawLine(prop, toUnit(top), toUnit(wrist), strokeWidth = stroke.width * 0.6f)
                drawCircle(prop, s * 0.02f, toUnit(top))
            }
            Pose.PoseProp.BAND -> {
                // Wavy band from the hand toward a floor anchor ahead of the figure.
                val anchor = Offset(wrist.x + 0.08f * s, (groundY * h + 0.01f * s))
                val mid = Offset(
                    (wrist.x + anchor.x) / 2f + 0.02f * s,
                    (wrist.y + anchor.y) / 2f,
                )
                drawLine(prop, toUnit(wrist), toUnit(mid), strokeWidth = stroke.width * 0.6f, cap = StrokeCap.Round)
                drawLine(prop, toUnit(mid), toUnit(anchor), strokeWidth = stroke.width * 0.6f, cap = StrokeCap.Round)
            }
            Pose.PoseProp.NONE, Pose.PoseProp.BODYWEIGHT -> Unit
        }
    }
}