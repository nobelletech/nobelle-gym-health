package com.nobelle.phone.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.nobelle.core.model.Equipment
import com.nobelle.core.model.ExerciseDefinition
import com.nobelle.core.model.ExperienceLevel
import com.nobelle.core.model.OutdoorSession
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TemplateExercise
import com.nobelle.core.model.TrainingPlan
import com.nobelle.phone.coach.CoachViewModel
import com.nobelle.phone.outdoor.OutdoorTrackService
import com.nobelle.phone.outdoor.OutdoorViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Coach (§5): exercise library, filters, template builder, guided session,
 * run/walk tracker. Visually distinct from My Health.
 *
 * The Exercise library card browses the bundled, reviewed catalog — text
 * instructions only (no media until it is curated, §5). The Templates card
 * builds owner templates synced via Drive; starting one prefills empty set
 * slots for the user to log — it never records reps or load (§8C).
 * The Outdoor card records device-GPS tracks; location is used only while a
 * user-started session records.
 */
@Composable
fun CoachScreen(
    outdoor: OutdoorViewModel.OutdoorUiState,
    coach: CoachViewModel.CoachUiState,
    onStartOutdoor: (activity: String) -> Unit,
    onStopOutdoor: () -> Unit,
    onDeleteOutdoor: (String) -> Unit,
    onSaveTemplate: (id: String?, name: String, category: String, exercises: List<TemplateExercise>) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    onGenerateWeek: () -> Unit,
    onAcceptPlan: () -> Unit,
    onDismissPlanSuggestion: () -> Unit,
    onPlanSessionStatus: (planId: String, sessionId: String, status: PlannedSession.Status) -> Unit,
    onStartPlannedSession: (planId: String, sessionId: String) -> Unit,
) {
    val context = LocalContext.current
    var showActivityPicker by remember { mutableStateOf(false) }
    var libraryOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ExerciseDefinition?>(null) }
    var builder by remember { mutableStateOf<TemplateDraft?>(null) }
    var pickingForBuilder by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            showActivityPicker = true
        }
    }

    if (showActivityPicker) {
        AlertDialog(
            onDismissRequest = { showActivityPicker = false },
            title = { Text("Outdoor session") },
            text = { Text("Choose an activity. Recording uses this device's GPS and needs a location fix.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showActivityPicker = false
                        onStartOutdoor(OutdoorSession.ACTIVITY_WALK)
                    }) { Text("Walk") }
                    TextButton(onClick = {
                        showActivityPicker = false
                        onStartOutdoor(OutdoorSession.ACTIVITY_RUN)
                    }) { Text("Run") }
                    TextButton(onClick = {
                        showActivityPicker = false
                        onStartOutdoor(OutdoorSession.ACTIVITY_CYCLE)
                    }) { Text("Cycle") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivityPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (libraryOpen || pickingForBuilder) {
        LibraryPickerDialog(
            catalog = coach.catalog,
            onDismiss = {
                libraryOpen = false
                pickingForBuilder = false
            },
            onPick = { exercise ->
                if (pickingForBuilder) {
                    builder = builder?.copy(
                        exercises = builder!!.exercises + DraftExercise(exercise.id, exercise.name)
                    )
                    pickingForBuilder = false
                } else {
                    detail = exercise
                    libraryOpen = false
                }
            },
        )
    }

    detail?.let { exercise ->
        ExerciseDetailDialog(exercise, coach.catalogById) { detail = null }
    }

    coach.suggestedPlan?.let { suggestion ->
        PlanReviewDialog(
            suggestion = suggestion,
            onAccept = onAcceptPlan,
            onDismiss = onDismissPlanSuggestion,
        )
    }

    builder?.let { draft ->
        TemplateBuilderDialog(
            draft = draft,
            onChange = { builder = it },
            onAddExercise = { pickingForBuilder = true },
            onDismiss = { builder = null },
            onSave = { name, category, exercises ->
                onSaveTemplate(draft.id, name, category, exercises)
                builder = null
            },
        )
    }

    ScreenColumn {
        ScreenTitle("Coach")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weekly plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your week at a glance — a suggestion you can change, not a prescription. " +
                        "Skipping is a choice, not a failure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val plan = coach.currentPlan
                if (plan == null) {
                    Text(
                        "No plan yet for this week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    plan.plannedSessions.sortedBy { it.localDate }.forEach { session ->
                        PlanSessionRow(
                            session = session,
                            onStart = { onStartPlannedSession(plan.id, session.id) },
                            onToggleSkip = {
                                onPlanSessionStatus(
                                    plan.id, session.id,
                                    if (session.status == PlannedSession.Status.SKIPPED) {
                                        PlannedSession.Status.PLANNED
                                    } else {
                                        PlannedSession.Status.SKIPPED
                                    },
                                )
                            },
                        )
                    }
                    if (plan.plannedSessions.isEmpty()) {
                        Text(
                            "This week has no planned sessions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onGenerateWeek,
                    enabled = coach.ownerAvailable && !coach.loading,
                ) { Text(if (plan == null) "Generate week" else "Re-generate week") }
            }
        }

        InfoCard(
            title = "Exercise library",
            body = "${coach.catalog.size} reviewed exercises — search by name, muscle, equipment and difficulty. " +
                "Instructions are text; they are not a live form assessment.",
            trailing = { OutlinedButton(onClick = { libraryOpen = true }) { Text("Browse") } },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Templates", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reusable workout plans you build yourself. Starting one creates empty set slots " +
                        "to fill in on the Today tab — nothing is logged for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                coach.templates.forEach { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${template.category} · ${template.exercises.size} exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            TextButton(onClick = { onStartTemplate(template.id) }) { Text("Start") }
                            TextButton(onClick = {
                                builder = TemplateDraft(
                                    id = template.id,
                                    name = template.name,
                                    category = template.category,
                                    exercises = template.exercises.map { ex ->
                                        DraftExercise(
                                            exerciseId = ex.exerciseId,
                                            name = coach.catalogById[ex.exerciseId]?.name ?: ex.exerciseId,
                                            sets = ex.sets.toString(),
                                            reps = ex.reps?.toString() ?: "",
                                            durationSec = ex.durationSec?.toString() ?: "",
                                            restSec = ex.restSec.toString(),
                                        )
                                    },
                                )
                            }) { Text("Edit") }
                            TextButton(onClick = { onDeleteTemplate(template.id) }) { Text("Delete") }
                        }
                    }
                }
                if (coach.templates.isEmpty()) {
                    Text(
                        "No templates yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { builder = TemplateDraft(id = null) },
                    enabled = coach.ownerAvailable,
                ) { Text("New template") }
            }
        }

        coach.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard(
            title = "Outdoor",
            body = if (outdoor.tracking) {
                val label = outdoor.activity?.replaceFirstChar { it.uppercase() } ?: "Outdoor"
                "$label recording — ${OutdoorTrackService.formatElapsed(outdoor.elapsedSec)} · " +
                    "${formatKm(outdoor.distanceMeters)} · ${outdoor.pointCount} GPS points"
            } else {
                "Run, walk and cycling tracking with GPS routes. Location is used only while you record an outdoor session; a location fix is required."
            },
            trailing = {
                if (outdoor.tracking) {
                    OutlinedButton(onClick = onStopOutdoor) { Text("Stop") }
                } else {
                    OutlinedButton(onClick = {
                        val fine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (fine) {
                            showActivityPicker = true
                        } else {
                            val wants = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                            if (Build.VERSION.SDK_INT >= 33) {
                                wants.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(wants.toTypedArray())
                        }
                    }) { Text("Start") }
                }
            },
        )

        outdoor.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (outdoor.sessions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent outdoor sessions", style = MaterialTheme.typography.titleMedium)
                    outdoor.sessions.take(10).forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.activity.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "${localDate(session.startedUtc)} · " +
                                        "${formatKm(session.distanceMeters)} · " +
                                        OutdoorTrackService.formatElapsed(session.durationSec) +
                                        " · ${session.pointCount} points",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onDeleteOutdoor(session.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

private data class DraftExercise(
    val exerciseId: String,
    val name: String,
    val sets: String = "3",
    val reps: String = "8",
    val durationSec: String = "",
    val restSec: String = "90",
)

private data class TemplateDraft(
    val id: String?,
    val name: String = "",
    val category: String = "Strength",
    val exercises: List<DraftExercise> = emptyList(),
)

@Composable
private fun LibraryPickerDialog(
    catalog: List<ExerciseDefinition>,
    onPick: (ExerciseDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var difficulty by remember { mutableStateOf<ExperienceLevel?>(null) }
    val muscles = remember(catalog) { catalog.flatMap { it.muscleGroups }.distinct().sorted() }
    val results = catalog.filter { def ->
        (query.isBlank() || def.name.contains(query.trim(), ignoreCase = true)) &&
            (muscle == null || def.muscleGroups.any { it.equals(muscle, ignoreCase = true) }) &&
            (equipment == null || def.equipment == equipment) &&
            (difficulty == null || def.difficulty == difficulty)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exercise library") },
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = muscle == null, onClick = { muscle = null }, label = { Text("All muscles") })
                    muscles.forEach { m ->
                        FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = equipment == null, onClick = { equipment = null }, label = { Text("All equipment") })
                    Equipment.entries.forEach { e ->
                        FilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e.name.lowercase().replace('_', ' ')) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = difficulty == null, onClick = { difficulty = null }, label = { Text("All levels") })
                    ExperienceLevel.entries.forEach { d ->
                        FilterChip(selected = difficulty == d, onClick = { difficulty = if (difficulty == d) null else d }, label = { Text(d.name.lowercase()) })
                    }
                }
                Text(
                    "${results.size} exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (results.isEmpty()) {
                    Text(
                        "No matching exercise — adjust the search or filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results) { def ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(def) }
                                    .padding(vertical = 8.dp)
                                    .semantics { contentDescription = def.name },
                            ) {
                                Text(def.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${def.muscleGroups.joinToString(", ")} · " +
                                        "${def.equipment.name.lowercase().replace('_', ' ')} · " +
                                        def.difficulty.name.lowercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ExerciseDetailDialog(
    exercise: ExerciseDefinition,
    byId: Map<String, ExerciseDefinition>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${exercise.muscleGroups.joinToString(", ")} · " +
                        "${exercise.equipment.name.lowercase().replace('_', ' ')} · " +
                        exercise.difficulty.name.lowercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Instructions", style = MaterialTheme.typography.titleSmall)
                exercise.instructions.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
                if (exercise.commonMistakes.isNotEmpty()) {
                    Text("Common mistakes", style = MaterialTheme.typography.titleSmall)
                    exercise.commonMistakes.forEach { mistake ->
                        Text("• $mistake", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val alternatives = listOfNotNull(
                    exercise.easierAlternativeId?.let { "Easier: ${byId[it]?.name ?: it}" },
                    exercise.harderAlternativeId?.let { "Harder: ${byId[it]?.name ?: it}" },
                )
                if (alternatives.isNotEmpty()) {
                    Text("Alternatives", style = MaterialTheme.typography.titleSmall)
                    alternatives.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                exercise.contraindicationNotes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Reviewed by ${exercise.author}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TemplateBuilderDialog(
    draft: TemplateDraft,
    onChange: (TemplateDraft) -> Unit,
    onAddExercise: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, exercises: List<TemplateExercise>) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "New template" else "Edit template") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("Template name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = { onChange(draft.copy(category = it)) },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(draft.exercises) { ex ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(ex.name, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    onChange(draft.copy(exercises = draft.exercises - ex))
                                }) { Text("Remove") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ex.sets,
                                    onValueChange = { v ->
                                        onChange(draft.copy(exercises = draft.exercises.map {
                                            if (it === ex) it.copy(sets = v) else it
                                        }))
                                    },
                                    label = { Text("Sets") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = ex.reps,
                                    onValueChange = { v ->
                                        onChange(draft.copy(exercises = draft.exercises.map {
                                            if (it === ex) it.copy(reps = v) else it
                                        }))
                                    },
                                    label = { Text("Reps") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = ex.durationSec,
                                    onValueChange = { v ->
                                        onChange(draft.copy(exercises = draft.exercises.map {
                                            if (it === ex) it.copy(durationSec = v) else it
                                        }))
                                    },
                                    label = { Text("Secs") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = ex.restSec,
                                    onValueChange = { v ->
                                        onChange(draft.copy(exercises = draft.exercises.map {
                                            if (it === ex) it.copy(restSec = v) else it
                                        }))
                                    },
                                    label = { Text("Rest s") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onAddExercise) { Text("Add exercise from library") }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val exercises = mutableListOf<TemplateExercise>()
                for (ex in draft.exercises) {
                    val sets = ex.sets.toIntOrNull()
                    val reps = ex.reps.trim().toIntOrNull()
                    val duration = ex.durationSec.trim().toIntOrNull()
                    val rest = ex.restSec.toIntOrNull()
                    if (sets == null || sets <= 0) {
                        error = "\"${ex.name}\": sets must be a positive number"; return@TextButton
                    }
                    if ((reps != null) == (duration != null)) {
                        error = "\"${ex.name}\": enter either reps or seconds, not both"; return@TextButton
                    }
                    if (reps != null && reps <= 0) {
                        error = "\"${ex.name}\": reps must be positive"; return@TextButton
                    }
                    if (duration != null && duration <= 0) {
                        error = "\"${ex.name}\": seconds must be positive"; return@TextButton
                    }
                    if (rest == null || rest < 0) {
                        error = "\"${ex.name}\": rest must be zero or more"; return@TextButton
                    }
                    exercises += TemplateExercise(
                        exerciseId = ex.exerciseId, sets = sets, reps = reps,
                        durationSec = duration, restSec = rest,
                    )
                }
                if (draft.name.isBlank() || draft.category.isBlank()) {
                    error = "Enter a template name and category"; return@TextButton
                }
                if (exercises.isEmpty()) {
                    error = "Add at least one exercise"; return@TextButton
                }
                onSave(draft.name, draft.category, exercises)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatKm(meters: Double): String =
    if (meters >= 1_000) "%.2f km".format(meters / 1_000) else "${meters.toInt()} m"

private fun localDate(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

@Composable
private fun PlanSessionRow(
    session: PlannedSession,
    onStart: () -> Unit,
    onToggleSkip: () -> Unit,
) {
    val weekday = runCatching {
        java.time.LocalDate.parse(session.localDate).dayOfWeek.name
            .lowercase().replaceFirstChar { it.uppercase() }
    }.getOrDefault(session.localDate)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$weekday ${session.localDate}", style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(session.category.replace('_', ' '))
                    session.location?.let { append(" · ${it.name.lowercase()}") }
                    append(" · ${session.status.name.lowercase()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            if (session.status == PlannedSession.Status.PLANNED) {
                TextButton(onClick = onStart) { Text("Start") }
            }
            if (session.status != PlannedSession.Status.COMPLETED) {
                TextButton(onClick = onToggleSkip) {
                    Text(if (session.status == PlannedSession.Status.SKIPPED) "Unskip" else "Skip")
                }
            }
        }
    }
}

@Composable
private fun PlanReviewDialog(
    suggestion: TrainingPlan,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggested week") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Generated by ${suggestion.generatedBy} for the week of " +
                        "${suggestion.weekStartDate}. It is a suggestion you can change, " +
                        "not a prescription — edit days and categories after accepting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                suggestion.plannedSessions.sortedBy { it.localDate }.forEach { session ->
                    val weekday = runCatching {
                        java.time.LocalDate.parse(session.localDate).dayOfWeek.name
                            .lowercase().replaceFirstChar { it.uppercase() }
                    }.getOrDefault(session.localDate)
                    Text(
                        "$weekday ${session.localDate} — ${session.category.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (suggestion.plannedSessions.isEmpty()) {
                    Text("No sessions suggested.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}
