package com.nobelle.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.nobelle.core.coaching.ExerciseCatalog
import com.nobelle.core.media.ExerciseFigure
import com.nobelle.core.model.ExerciseDefinition

/**
 * Wear exercise preview (§5 media pipeline): a compact, honestly-labelled
 * "preview set" of the same catalog entries the phone ships — not a separate
 * list of the 16 motion primitives. Captions only on the watch (no TTS);
 * tap toggles the figure loop, a horizontal swipe moves to the next
 * exercise. Technique review status is shown as pending.
 */
val WEAR_PREVIEW_EXERCISE_IDS = listOf(
    "push-up", "back-squat", "conventional-deadlift", "barbell-bench-press",
    "barbell-bent-over-row", "lat-pulldown", "overhead-press", "reverse-lunge",
    "hip-thrust", "bicep-curl", "plank", "crunch",
)

@Composable
fun WearExercisePreview(onBack: () -> Unit) {
    val catalog = remember { ExerciseCatalog.byId() }
    val exercises = remember { WEAR_PREVIEW_EXERCISE_IDS.mapNotNull { catalog[it] } }
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    val index = detailIndex
    if (index != null) {
        WearExerciseDetailPager(
            exercises = exercises,
            initialPage = index,
            onBack = { detailIndex = null },
        )
        return
    }

    BackHandler(onBack = onBack)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                "Exercise preview",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                "Preview set — ${exercises.size} of ${catalog.size} catalog exercises. " +
                    "Schematic figures only; videos are a pending content milestone. " +
                    "Captions only on the watch.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        items(exercises) { exercise ->
            Button(onClick = { detailIndex = exercises.indexOf(exercise) }) {
                Text(exercise.name, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WearExerciseDetailPager(
    exercises: List<ExerciseDefinition>,
    initialPage: Int,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val pagerState = rememberPagerState(initialPage = initialPage) { exercises.size }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        WearExerciseDetail(exercises[page])
    }
}

@Composable
private fun WearExerciseDetail(exercise: ExerciseDefinition) {
    var playing by remember { mutableStateOf(true) }
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        item {
            ExerciseFigure(
                primitive = exercise.motion,
                animated = playing,
                modifier = Modifier.size(110.dp).clickable { playing = !playing },
            )
        }
        item {
            Text(
                if (playing) "Tap figure to pause" else "Tap figure to play",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                "Swipe for next exercise",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                "Technique review pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                "Schematic figure — not a substitute for the instructions.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        exercise.instructions.forEachIndexed { index, step ->
            item {
                Text(
                    "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}