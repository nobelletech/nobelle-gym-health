package com.nobelle.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobelle.phone.fasting.FastingViewModel
import com.nobelle.phone.gym.GymVisitViewModel
import com.nobelle.phone.health.HealthConnectViewModel
import com.nobelle.phone.progress.PersonalBest
import com.nobelle.phone.progress.ProgressReport
import com.nobelle.phone.progress.ProgressSummary
import com.nobelle.phone.progress.WeekSummary
import com.nobelle.phone.workout.WorkoutViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Progress (§3): deterministic summaries of the owner's own stored records —
 * ISO-week activity counts, gym attendance and per-exercise heaviest sets.
 * Descriptive counts only, kept separate from medical interpretation.
 */
@Composable
fun ProgressScreen(
    workout: WorkoutViewModel.WorkoutUiState,
    gymVisits: GymVisitViewModel.GymVisitUiState,
    fasting: FastingViewModel.FastingUiState,
    healthConnect: HealthConnectViewModel.HealthConnectUiState,
    onRefresh: () -> Unit,
) {
    // Re-read the owner-scoped stores each time the tab opens so records
    // applied by background sync show up without an app restart.
    LaunchedEffect(Unit) { onRefresh() }

    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val report = remember(
        workout.history, workout.watchWorkouts, gymVisits.visits,
        fasting.history, healthConnect.storedObservations, today,
    ) {
        ProgressSummary.summarize(
            workouts = workout.history,
            gymVisits = gymVisits.visits,
            fasts = fasting.history,
            watchWorkouts = workout.watchWorkouts,
            observations = healthConnect.storedObservations,
            today = today,
            zone = zone,
        )
    }

    ScreenColumn {
        ScreenTitle("Progress")

        if (!(workout.ownerAvailable || gymVisits.ownerAvailable ||
                fasting.ownerAvailable || healthConnect.ownerAvailable)
        ) {
            InfoCard(
                title = "Progress",
                body = "Connect your Google identity in Profile once; summaries then build " +
                    "from records stored on this device.",
            )
        } else {
            WeekCard(report)
            PersonalBestsCard(report.personalBests)
            AttendanceCard(report)
            InfoCard(
                title = "About these summaries",
                body = "Counts and bests come only from records stored on this device — " +
                    "descriptive, not medical advice.",
            )
        }
    }
}

@Composable
private fun WeekCard(report: ProgressReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Text(
                "${report.weekStart} – ${report.weekEnd}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WeekLines(report.thisWeek)
            Text(
                "Last week — ${weekLine(report.lastWeek)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekLines(week: WeekSummary) {
    val nothingLogged = week.workouts == 0 && week.sets == 0 && week.gymDays == 0 &&
        week.completedFasts == 0 && week.watchWorkouts == 0 && week.steps == null
    if (nothingLogged) {
        Text(
            "Nothing logged this week yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        "${week.workouts.plural("workout")} · ${week.sets.plural("set")} logged",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(week.gymDays.plural("gym day"), style = MaterialTheme.typography.bodyMedium)
    Text(week.completedFasts.plural("completed fast"), style = MaterialTheme.typography.bodyMedium)
    Text(
        week.watchWorkouts.plural("watch workout") +
            if (week.watchWorkouts > 0) " · ${formatDuration(week.watchDurationSec)}" else "",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        week.steps?.let { "${formatSteps(it)} steps" } ?: "No step records",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PersonalBestsCard(bests: List<PersonalBest>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personal bests", style = MaterialTheme.typography.titleMedium)
            if (bests.isEmpty()) {
                Text(
                    "Heaviest confirmed sets appear here once you log reps and load.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            bests.forEach { best ->
                Text(
                    "${best.exerciseId} · ${best.loadKg.formatKg()} kg × ${best.reps}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AttendanceCard(report: ProgressReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Attendance", style = MaterialTheme.typography.titleMedium)
            if (report.totalGymDays == 0) {
                Text(
                    "No gym visits recorded yet — check in from the Today tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${report.totalGymDays.plural("gym day")} recorded · " +
                        report.thisWeek.gymDays.plural("day") + " this week",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun weekLine(week: WeekSummary): String = buildString {
    append("${week.workouts.plural("workout")} · ${week.sets.plural("set")}")
    append(" · ${week.gymDays.plural("gym day")}")
    append(" · ${week.completedFasts.plural("fast")}")
    append(" · ${week.watchWorkouts.plural("watch workout")}")
    week.steps?.let { append(" · ${formatSteps(it)} steps") }
}

private fun Int.plural(noun: String): String =
    "$this $noun${if (this == 1) "" else "s"}"

private fun Double.formatKg(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun formatSteps(steps: Double): String = "%,d".format(steps.toLong())

private fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h %02dm".format((seconds % 3600) / 60)
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}
