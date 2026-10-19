package com.nobelle.phone.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.SetType
import com.nobelle.core.model.WorkoutState
import com.nobelle.phone.gym.GymCategoryCatalog
import com.nobelle.phone.gym.GymVisitInput
import com.nobelle.phone.gym.GymVisitInputParser
import com.nobelle.phone.gym.GymVisitViewModel
import com.nobelle.phone.wear.WatchEnrollViewModel
import com.nobelle.phone.workout.ExercisePreset
import com.nobelle.phone.workout.ExercisePresetCatalog
import com.nobelle.phone.workout.SetEntryInput
import com.nobelle.phone.workout.SetEntryParser
import com.nobelle.phone.workout.WorkoutViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Today (§3): offline manual workout journey — start/pause/resume/finish and
 * per-set logging, all persisted locally under the stable owner identity.
 */
@Composable
fun TodayScreen(
    workout: WorkoutViewModel.WorkoutUiState,
    gymVisits: GymVisitViewModel.GymVisitUiState,
    onStart: () -> Unit,
    onPauseOrResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onAddSet: (SetEntryInput) -> Unit,
    onLogVisitNow: (gymLabel: String?, category: String?, notes: String?) -> Unit,
    onBackdateVisit: (GymVisitInput) -> Unit,
    onEndVisit: (String) -> Unit,
    onEditVisit: (String, GymVisitInput) -> Unit,
    onDeleteVisit: (String) -> Unit,
    watchEnroll: WatchEnrollViewModel.UiState,
    onWatchConfirm: () -> Unit,
    onWatchReject: () -> Unit,
    onWatchForget: () -> Unit,
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

        WatchEnrollCard(
            watch = watchEnroll,
            onConfirm = onWatchConfirm,
            onReject = onWatchReject,
            onForget = onWatchForget,
        )

        InfoCard(
            title = "Sync",
            body = if (watchEnroll.ownerAvailable) {
                "Workouts recorded here or on a paired watch are stored in this device's " +
                    "encrypted outbox and upload to your Drive app data folder while Drive " +
                    "is connected. Queued for upload: " +
                    (if (watchEnroll.outboxPendingCapped) {
                        "${watchEnroll.outboxPending}+"
                    } else {
                        "${watchEnroll.outboxPending}"
                    }) + ". " +
                    "Applying remote changes onto this device is not implemented yet."
            } else {
                "Workouts are stored encrypted on this device. Connect your Google identity " +
                    "in Profile to enable Drive upload."
            },
        )

        gymVisits.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        GymAttendanceCard(
            gymVisits = gymVisits,
            onLogNow = onLogVisitNow,
            onBackdate = onBackdateVisit,
            onEnd = onEndVisit,
            onEdit = onEditVisit,
            onDelete = onDeleteVisit,
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
                        val tightPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        OutlinedButton(
                            onClick = onPauseOrResume,
                            enabled = !workout.loading,
                            contentPadding = tightPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (session.state == WorkoutState.ACTIVE) "Pause" else "Resume",
                                maxLines = 1,
                            )
                        }
                        Button(
                            onClick = onFinish,
                            enabled = !workout.loading,
                            contentPadding = tightPadding,
                            modifier = Modifier.weight(1f),
                        ) { Text("Finish", maxLines = 1) }
                        OutlinedButton(
                            onClick = onRequestDiscard,
                            enabled = !workout.loading,
                            contentPadding = tightPadding,
                            modifier = Modifier.weight(1f),
                        ) { Text("Discard", maxLines = 1) }
                    }
                }
            }
        }
    }
}

/**
 * Watch enrollment card (§7). Pairing is optional and owner-scoped; the card
 * never displays or stores the enrollment token — only the 6-digit code the
 * user compares against the watch screen.
 */
@Composable
private fun WatchEnrollCard(
    watch: WatchEnrollViewModel.UiState,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onForget: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Watch", style = MaterialTheme.typography.titleMedium)
            val pending = watch.pending
            val enrolled = watch.enrolled
            when {
                !watch.ownerAvailable -> {
                    Text(
                        "Watch pairing is unavailable until you connect your Google identity in Profile. Phone-only use is fully supported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pending != null -> {
                    Text(
                        "Pairing request from ${pending.watchModel}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        pending.code,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Compare with the code shown on your watch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConfirm,
                            enabled = !watch.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Confirm") }
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !watch.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Reject") }
                    }
                }
                enrolled != null -> {
                    val enrolledDate = enrolled.enrolledUtc
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    Text(
                        "Enrolled: ${enrolled.watchModel} · since $enrolledDate",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Workout events received from watch: ${enrolled.eventsReceived}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (enrolled.eventsReceived > 0) {
                        val last = enrolled.lastContactUtc
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            "Last watch event: ${last.date} " +
                                "%02d:%02d".format(last.hour, last.minute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onForget, enabled = !watch.busy) {
                        Text("Forget watch")
                    }
                }
                else -> {
                    Text(
                        "Not paired. Pairing is optional — phone-only use is fully supported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "To pair, open the Nobelle app on your watch and tap \"Enroll watch\" — it will show a code to confirm here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            watch.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
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
    var manualMode by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<ExercisePreset?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log a set", style = MaterialTheme.typography.titleMedium)
            if (manualMode) {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = {
                        exercise = it
                        selectedPreset = null
                    },
                    label = { Text("Exercise name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        manualMode = false
                        showExercisePicker = true
                    },
                    enabled = enabled,
                ) { Text("Browse exercise library") }
            } else {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Exercise") },
                    placeholder = { Text("None selected") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showExercisePicker = true },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Choose exercise" },
                    ) { Text(if (exercise.isBlank()) "Choose exercise" else "Change") }
                    TextButton(
                        onClick = { manualMode = true },
                        enabled = enabled,
                    ) { Text("Manual entry") }
                }
                selectedPreset?.let { preset ->
                    Text(
                        "${preset.category} · ${preset.equipment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Suggested defaults — edit any field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Choose a load you already know you can use safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reps, { reps = it }, "Reps", Modifier.weight(1f))
                NumberField(load, { load = it }, "Load", Modifier.weight(1f), decimal = true, suffix = "kg")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(duration, { duration = it }, "Duration", Modifier.weight(1f), suffix = "s")
                NumberField(rest, { rest = it }, "Rest", Modifier.weight(1f), suffix = "s")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(rpe, { rpe = it }, "RPE", Modifier.weight(1f), decimal = true)
                NumberField(rir, { rir = it }, "RIR", Modifier.weight(1f), decimal = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tempo,
                    onValueChange = { tempo = it },
                    label = { Text("Tempo", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = { Text("Superset", maxLines = 1) },
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

    if (showExercisePicker) {
        ExercisePickerDialog(
            onDismiss = { showExercisePicker = false },
            onManual = {
                showExercisePicker = false
                manualMode = true
            },
            onSelect = { preset ->
                exercise = preset.name
                reps = preset.reps?.toString().orEmpty()
                duration = preset.durationSec?.toString().orEmpty()
                rest = preset.restSec.toString()
                rpe = preset.rpe?.formatWithoutNeedlessDecimal().orEmpty()
                rir = preset.rir?.formatWithoutNeedlessDecimal().orEmpty()
                tempo = preset.tempo.orEmpty()
                setType = preset.setType
                load = ""
                selectedPreset = preset
                manualMode = false
                showExercisePicker = false
            },
        )
    }
}

@Composable
private fun ExercisePickerDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onSelect: (ExercisePreset) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "Search exercise library" },
                )
                val results = ExercisePresetCatalog.search(query)
                Text(
                    if (query.isBlank()) "${results.size} exercises" else "${results.size} matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (results.isEmpty()) {
                    Text(
                        "No matching exercise — try another search or enter it manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results) { preset ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onSelect(preset) }
                                    .padding(vertical = 8.dp)
                                    .semantics { contentDescription = preset.name },
                            ) {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${preset.category} · ${preset.equipment}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        dismissButton = {
            TextButton(onClick = onManual) { Text("Enter manually") }
        },
    )
}

@Composable
private fun VisitDateTimeSelectors(
    date: String,
    time: String,
    enabled: Boolean,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val localDate = runCatching { LocalDate.parse(date) }
        .getOrElse { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val localTime = runCatching { LocalTime.parse(time) }
        .getOrElse { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateChange("%04d-%02d-%02d".format(year, month + 1, dayOfMonth))
                    },
                    localDate.year,
                    localDate.monthNumber - 1,
                    localDate.dayOfMonth,
                ).show()
            },
            enabled = enabled,
            modifier = Modifier.weight(1f)
                .semantics { contentDescription = "Select date, ${localDate.friendly()}" },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Date")
                Text(
                    localDate.friendly(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute -> onTimeChange("%02d:%02d".format(hourOfDay, minute)) },
                    localTime.hour,
                    localTime.minute,
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            enabled = enabled,
            modifier = Modifier.weight(1f)
                .semantics { contentDescription = "Select time, $time" },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Time")
                Text(time, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun LocalDate.friendly(): String =
    "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $dayOfMonth, $year"

private fun Double.formatWithoutNeedlessDecimal(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

@Composable
private fun GymCategorySelector(
    category: String,
    onCategoryChange: (String) -> Unit,
    enabled: Boolean,
) {
    // A nonblank value outside the catalog (e.g. editing existing custom data)
    // starts in manual mode so it stays editable and is never lost.
    var manual by remember {
        mutableStateOf(category.isNotBlank() && category !in GymCategoryCatalog.all)
    }
    var showPicker by remember { mutableStateOf(false) }

    if (manual) {
        OutlinedTextField(
            value = category,
            onValueChange = onCategoryChange,
            label = { Text("Manual category (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                manual = false
                showPicker = true
            },
            enabled = enabled,
        ) { Text("Browse category list") }
    } else {
        OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category (optional)") },
            placeholder = { Text("None selected") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Choose category" },
            ) { Text(if (category.isBlank()) "Choose category" else "Change") }
            TextButton(onClick = { manual = true }, enabled = enabled) {
                Text("Manual category")
            }
        }
    }

    if (showPicker) {
        GymCategoryPickerDialog(
            onDismiss = { showPicker = false },
            onManual = {
                showPicker = false
                manual = true
            },
            onSelect = { chosen ->
                onCategoryChange(chosen)
                manual = false
                showPicker = false
            },
        )
    }
}

@Composable
private fun GymCategoryPickerDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search categories") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "Search category list" },
                )
                val results = GymCategoryCatalog.search(query)
                Text(
                    if (query.isBlank()) "${results.size} categories" else "${results.size} matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (results.isEmpty()) {
                    Text(
                        "No matching category — try another search or enter it manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results) { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelect(name) }
                                    .padding(vertical = 10.dp)
                                    .semantics { contentDescription = name },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        dismissButton = {
            TextButton(onClick = onManual) { Text("Enter manually") }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        suffix = suffix?.let { unit -> { Text(unit) } },
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

@Composable
private fun GymAttendanceCard(
    gymVisits: GymVisitViewModel.GymVisitUiState,
    onLogNow: (gymLabel: String?, category: String?, notes: String?) -> Unit,
    onBackdate: (GymVisitInput) -> Unit,
    onEnd: (String) -> Unit,
    onEdit: (String, GymVisitInput) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showLogNow by remember { mutableStateOf(false) }
    var showBackdate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GymVisit?>(null) }
    var deleting by remember { mutableStateOf<GymVisit?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gym check-in", style = MaterialTheme.typography.titleMedium)
            if (!gymVisits.ownerAvailable) {
                Text(
                    "Connect your Google identity in Profile once; attendance then remains available offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {}, enabled = false) { Text("Log visit") }
            } else {
                val days = gymVisits.attendanceDays
                val count = gymVisits.visits.size
                Text(
                    "$days attendance day${if (days == 1) "" else "s"} · " +
                        "$count visit${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showLogNow = true }, enabled = !gymVisits.loading) {
                        Text("Log now")
                    }
                    OutlinedButton(onClick = { showBackdate = true }, enabled = !gymVisits.loading) {
                        Text("Backdate")
                    }
                }
                gymVisits.visits.take(5).forEach { visit ->
                    VisitRow(
                        visit = visit,
                        enabled = !gymVisits.loading,
                        onEdit = { editing = visit },
                        onEnd = { onEnd(visit.id) },
                        onDelete = { deleting = visit },
                    )
                }
            }
        }
    }

    if (showLogNow) {
        LogNowDialog(
            enabled = !gymVisits.loading,
            onDismiss = { showLogNow = false },
            onSubmit = { label, category, notes ->
                showLogNow = false
                onLogNow(label, category, notes)
            },
        )
    }

    if (showBackdate) {
        val localNow = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        VisitFormDialog(
            title = "Backdate a visit",
            confirmLabel = "Log visit",
            zoneId = TimeZone.currentSystemDefault().id,
            initialDate = localNow.date.toString(),
            initialTime = "%02d:%02d".format(localNow.hour, localNow.minute),
            enabled = !gymVisits.loading,
            onDismiss = { showBackdate = false },
            onSubmit = { input ->
                showBackdate = false
                onBackdate(input)
            },
        )
    }

    editing?.let { visit ->
        val localStart = visit.startInstant.toLocalDateTime(TimeZone.of(visit.originalTimeZone))
        VisitFormDialog(
            title = "Edit visit",
            confirmLabel = "Save",
            zoneId = visit.originalTimeZone,
            initialDate = localStart.date.toString(),
            initialTime = "%02d:%02d".format(localStart.hour, localStart.minute),
            initialLabel = visit.gymLabel.orEmpty(),
            initialCategory = visit.category.orEmpty(),
            initialNotes = visit.notes.orEmpty(),
            enabled = !gymVisits.loading,
            onDismiss = { editing = null },
            onSubmit = { input ->
                editing = null
                onEdit(visit.id, input)
            },
        )
    }

    deleting?.let { visit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this visit?") },
            text = { Text("This marks the visit as deleted on this device; a deletion is queued for Drive sync once upload exists.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(visit.id)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun VisitRow(
    visit: GymVisit,
    enabled: Boolean,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val zone = runCatching { TimeZone.of(visit.originalTimeZone) }
            .getOrElse { TimeZone.currentSystemDefault() }
        val startLocal = visit.startInstant.toLocalDateTime(zone)
        val status = visit.endInstant
            ?.toLocalDateTime(zone)
            ?.let { "Ended %02d:%02d".format(it.hour, it.minute) }
            ?: "In progress"
        Text(
            "${visit.localAttendanceDate} · %02d:%02d".format(startLocal.hour, startLocal.minute),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append(status)
                visit.gymLabel?.let { append(" · $it") }
                visit.category?.let { append(" · $it") }
                if (visit.workoutIds.isNotEmpty()) {
                    append(" · ${visit.workoutIds.size} linked workout")
                    if (visit.workoutIds.size != 1) append("s")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
            if (visit.endInstant == null) {
                TextButton(onClick = onEnd, enabled = enabled) { Text("End") }
            }
            TextButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
        }
    }
}

@Composable
private fun LogNowDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (gymLabel: String?, category: String?, notes: String?) -> Unit,
) {
    var gymLabel by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a visit now") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gymLabel,
                    onValueChange = { gymLabel = it },
                    label = { Text("Gym (optional)") },
                    singleLine = true,
                )
                GymCategorySelector(
                    category = category,
                    onCategoryChange = { category = it },
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = { onSubmit(gymLabel, category, notes) },
            ) { Text("Log visit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun VisitFormDialog(
    title: String,
    confirmLabel: String,
    zoneId: String,
    initialDate: String,
    initialTime: String,
    initialLabel: String = "",
    initialCategory: String = "",
    initialNotes: String = "",
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (GymVisitInput) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(initialTime) }
    var gymLabel by remember { mutableStateOf(initialLabel) }
    var category by remember { mutableStateOf(initialCategory) }
    var notes by remember { mutableStateOf(initialNotes) }
    var formError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VisitDateTimeSelectors(
                    date = date,
                    time = time,
                    enabled = enabled,
                    onDateChange = { date = it },
                    onTimeChange = { time = it },
                )
                OutlinedTextField(
                    value = gymLabel,
                    onValueChange = { gymLabel = it },
                    label = { Text("Gym (optional)") },
                    singleLine = true,
                )
                GymCategorySelector(
                    category = category,
                    onCategoryChange = { category = it },
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                )
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = {
                    when (val parsed = GymVisitInputParser.parse(
                        date = date, time = time, zoneId = zoneId,
                        gymLabel = gymLabel, category = category, notes = notes,
                    )) {
                        is GymVisitInputParser.Result.Ok -> onSubmit(parsed.input)
                        is GymVisitInputParser.Result.Invalid -> formError = parsed.message
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
