package com.nobelle.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.SetType
import com.nobelle.core.model.WorkoutState
import com.nobelle.phone.workout.SetEntryInput
import com.nobelle.phone.workout.SetEntryParser
import com.nobelle.phone.workout.WorkoutViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Today (§3): offline manual workout journey — start/pause/resume/finish and
 * per-set logging, all persisted locally under the stable owner identity.
 */
@Composable
fun TodayScreen(
    workout: WorkoutViewModel.WorkoutUiState,
    onStart: () -> Unit,
    onPauseOrResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onAddSet: (SetEntryInput) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }

    ScreenColumn {
        ScreenTitle("Today")

        workout.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        WorkoutCard(
            workout = workout,
            onStart = onStart,
            onPauseOrResume = onPauseOrResume,
            onFinish = onFinish,
            onRequestDiscard = { confirmDiscard = true },
        )

        val session = workout.session
        if (session != null &&
            (session.state == WorkoutState.ACTIVE || session.state == WorkoutState.PAUSED)
        ) {
            SetEntryCard(
                enabled = !workout.loading,
                onAddSet = onAddSet,
            )
            SetsCard(sets = workout.sets)
        }

        InfoCard(
            title = "Watch",
            body = "Not paired. Pairing is optional — phone-only use is fully supported.",
            trailing = { OutlinedButton(onClick = {}) { Text("Pair") } },
        )

        InfoCard(
            title = "Sync",
            body = "Stored encrypted on this device. Completed workouts are queued locally; automatic Drive upload is not implemented yet.",
        )

        InfoCard(
            title = "Gym check-in",
            body = "One tap logs a visit — even without a recorded workout.",
            trailing = { OutlinedButton(onClick = {}) { Text("Log visit") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("This marks the unsynced workout as discarded and hides it from the active workout view.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDiscard()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutViewModel.WorkoutUiState,
    onStart: () -> Unit,
    onPauseOrResume: () -> Unit,
    onFinish: () -> Unit,
    onRequestDiscard: () -> Unit,
) {
    val session = workout.session
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !workout.ownerAvailable && session == null -> {
                    Text("Today's workout", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect your Google identity in Profile once; workouts then remain available offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onStart, enabled = false) { Text("Start") }
                }
                session == null -> {
                    Text("Strength", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No workout in progress — everything you log stays on this device until sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onStart, enabled = !workout.loading) { Text("Start workout") }
                }
                else -> {
                    val status = if (session.state == WorkoutState.PAUSED) "Paused" else "Active"
                    val started = session.startedUtc
                        ?.toLocalDateTime(TimeZone.currentSystemDefault())
                        ?.let { "%02d:%02d".format(it.hour, it.minute) }
                    Text("$status workout", style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            if (started != null) append("Started $started · ")
                            append("${workout.sets.size} set${if (workout.sets.size == 1) "" else "s"} logged")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPauseOrResume, enabled = !workout.loading) {
                            Text(if (session.state == WorkoutState.ACTIVE) "Pause" else "Resume")
                        }
                        Button(onClick = onFinish, enabled = !workout.loading) { Text("Finish") }
                        OutlinedButton(onClick = onRequestDiscard, enabled = !workout.loading) {
                            Text("Discard")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetEntryCard(enabled: Boolean, onAddSet: (SetEntryInput) -> Unit) {
    var exercise by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var load by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var rest by remember { mutableStateOf("") }
    var rpe by remember { mutableStateOf("") }
    var rir by remember { mutableStateOf("") }
    var tempo by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var substitutedFor by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pain by remember { mutableStateOf(false) }
    var setType by remember { mutableStateOf(SetType.STANDARD) }
    var formError by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log a set", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = exercise,
                onValueChange = { exercise = it },
                label = { Text("Exercise") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reps, { reps = it }, "Reps", Modifier.weight(1f))
                NumberField(load, { load = it }, "Load kg", Modifier.weight(1f), decimal = true)
                NumberField(duration, { duration = it }, "Duration s", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(rest, { rest = it }, "Rest s", Modifier.weight(1f))
                NumberField(rpe, { rpe = it }, "RPE", Modifier.weight(1f), decimal = true)
                NumberField(rir, { rir = it }, "RIR", Modifier.weight(1f), decimal = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tempo,
                    onValueChange = { tempo = it },
                    label = { Text("Tempo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = { Text("Group (superset)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = substitutedFor,
                onValueChange = { substitutedFor = it },
                label = { Text("Substituted for (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                SetType.entries.forEach { type ->
                    FilterChip(
                        selected = setType == type,
                        onClick = { setType = type },
                        label = { Text(type.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pain, onCheckedChange = { pain = it })
                Text("Pain during this set")
            }
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = enabled,
                onClick = {
                    when (val parsed = SetEntryParser.parse(
                        exerciseId = exercise, reps = reps, loadKg = load, durationSec = duration,
                        restSec = rest, rpe = rpe, rir = rir, tempo = tempo, painFlag = pain,
                        notes = notes, setType = setType, groupId = groupId,
                        substitutedForExerciseId = substitutedFor,
                    )) {
                        is SetEntryParser.Result.Ok -> {
                            formError = null
                            onAddSet(parsed.entry)
                        }
                        is SetEntryParser.Result.Invalid -> formError = parsed.message
                    }
                },
            ) { Text("Add set") }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier,
    )
}

@Composable
private fun SetsCard(sets: List<ExerciseSet>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sets", style = MaterialTheme.typography.titleMedium)
            if (sets.isEmpty()) {
                Text(
                    "Nothing logged yet — add your first set above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sets.sortedBy { it.setIndex }.forEach { set ->
                Text(set.describe(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun ExerciseSet.describe(): String {
    val parts = mutableListOf("#${setIndex + 1} $exerciseId")
    reps?.let { parts += "$it reps" }
    loadKg?.let { parts += "@ ${it}kg" }
    durationSec?.let { parts += "${it}s" }
    restSec?.let { parts += "rest ${it}s" }
    rpe?.let { parts += "RPE $it" }
    rir?.let { parts += "RIR $it" }
    tempo?.let { parts += "tempo $it" }
    if (setType != SetType.STANDARD) parts += setType.label
    if (painFlag) parts += "pain"
    notes?.let { parts += "— $it" }
    return parts.joinToString(" · ")
}

private val SetType.label: String
    get() = when (this) {
        SetType.STANDARD -> "Standard"
        SetType.WARM_UP -> "Warm-up"
        SetType.DROP -> "Drop"
        SetType.FAILURE -> "Failure"
        SetType.TIMED -> "Timed"
    }
