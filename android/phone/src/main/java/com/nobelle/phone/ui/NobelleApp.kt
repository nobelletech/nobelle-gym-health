package com.nobelle.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobelle.core.health.HealthConnectCategory
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TemplateExercise
import com.nobelle.phone.coach.CoachViewModel
import com.nobelle.phone.fasting.FastingBackdateInput
import com.nobelle.phone.fasting.FastingViewModel
import com.nobelle.phone.gym.GymVisitInput
import com.nobelle.phone.health.ManualEntryInput
import com.nobelle.phone.gym.GymVisitViewModel
import com.nobelle.phone.health.HealthConnectViewModel
import com.nobelle.phone.outdoor.OutdoorViewModel
import com.nobelle.phone.ui.screens.CoachScreen
import com.nobelle.phone.ui.screens.MyHealthScreen
import com.nobelle.phone.ui.screens.ProfileScreen
import com.nobelle.phone.ui.screens.ProgressScreen
import com.nobelle.phone.ui.screens.TodayScreen
import com.nobelle.phone.wear.WatchEnrollViewModel
import com.nobelle.phone.workout.SetEntryInput
import com.nobelle.phone.workout.SlotLogEntry
import com.nobelle.phone.workout.WorkoutViewModel

// Primary navigation (§3): Today | Coach | My Health | Progress | Profile
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.GridView),
    COACH("coach", "Coach", Icons.Filled.FitnessCenter),
    HEALTH("health", "My Health", Icons.Filled.Favorite),
    PROGRESS("progress", "Progress", Icons.Filled.Leaderboard),
    PROFILE("profile", "Profile", Icons.Filled.Person),
}

@Composable
fun NobelleApp(
    driveStatus: String,
    driveConnected: Boolean,
    driveDiagnosticStatus: String?,
    driveDiagnosticRunning: Boolean,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onTestDrive: () -> Unit,
    workout: WorkoutViewModel.WorkoutUiState,
    onStartWorkout: () -> Unit,
    onPauseOrResumeWorkout: () -> Unit,
    onFinishWorkout: () -> Unit,
    onDiscardWorkout: () -> Unit,
    onAddSet: (SetEntryInput) -> Unit,
    onCompleteSlot: (SlotLogEntry) -> Unit,
    onSwapSlot: (setId: String, exerciseId: String) -> Unit,
    gymVisits: GymVisitViewModel.GymVisitUiState,
    onLogVisitNow: (gymLabel: String?, category: String?, notes: String?) -> Unit,
    onBackdateVisit: (GymVisitInput) -> Unit,
    onEndVisit: (String) -> Unit,
    onEditVisit: (String, GymVisitInput) -> Unit,
    onDeleteVisit: (String) -> Unit,
    watchEnroll: WatchEnrollViewModel.UiState,
    onWatchConfirm: () -> Unit,
    onWatchReject: () -> Unit,
    onWatchForget: () -> Unit,
    fasting: FastingViewModel.FastingUiState,
    onFastingEnable: (fastingHours: Int, startLocalTime: String) -> Unit,
    onFastingDisable: () -> Unit,
    onFastingStart: () -> Unit,
    onFastingEnd: () -> Unit,
    onFastingCancel: () -> Unit,
    onFastingBackdate: (FastingBackdateInput) -> Unit,
    onFastingDelete: (String) -> Unit,
    onFastingTick: () -> Unit,
    healthConnect: HealthConnectViewModel.HealthConnectUiState,
    onToggleHealthCategory: (HealthConnectCategory) -> Unit,
    onRequestHealthAccess: () -> Unit,
    onImportHealth: () -> Unit,
    onRefreshHealthAccess: () -> Unit,
    onAddManualHealthEntry: (ManualEntryInput) -> Unit,
    onDeleteManualHealthEntry: (String) -> Unit,
    showHealthRationale: Boolean,
    onDismissHealthRationale: () -> Unit,
    outdoor: OutdoorViewModel.OutdoorUiState,
    coach: CoachViewModel.CoachUiState,
    onStartOutdoor: (String) -> Unit,
    onStopOutdoor: () -> Unit,
    onDeleteOutdoor: (String) -> Unit,
    onSaveTemplate: (id: String?, name: String, category: String, exercises: List<TemplateExercise>) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    onGenerateWeek: () -> Unit,
    onAcceptPlan: () -> Unit,
    onDismissPlanSuggestion: () -> Unit,
    onPlanSessionStatus: (String, String, PlannedSession.Status) -> Unit,
    onStartPlannedSession: (String, String) -> Unit,
    onProgressRefresh: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Nobelle Health header + icon strip (Samsung Health-style): the app
    // name sits above a pill row of section icons; the active section gets a
    // filled accent disc. Sections are unlabeled icons — labels remain in
    // each screen's own title for accessibility.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    "Nobelle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Destination.entries.forEach { d ->
                            val selected = current == d.route
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.ui.graphics.Color.Transparent
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        nav.navigate(d.route) {
                                            popUpTo(nav.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Icon(
                                    d.icon,
                                    contentDescription = d.label,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destination.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.TODAY.route) {
                TodayScreen(
                    workout = workout,
                    gymVisits = gymVisits,
                    onStart = onStartWorkout,
                    onPauseOrResume = onPauseOrResumeWorkout,
                    onFinish = onFinishWorkout,
                    onDiscard = onDiscardWorkout,
                    onAddSet = onAddSet,
                    onCompleteSlot = onCompleteSlot,
                    onSwapSlot = onSwapSlot,
                    onLogVisitNow = onLogVisitNow,
                    onBackdateVisit = onBackdateVisit,
                    onEndVisit = onEndVisit,
                    onEditVisit = onEditVisit,
                    onDeleteVisit = onDeleteVisit,
                    watchEnroll = watchEnroll,
                    onWatchConfirm = onWatchConfirm,
                    onWatchReject = onWatchReject,
                    onWatchForget = onWatchForget,
                )
            }
            composable(Destination.COACH.route) {
                CoachScreen(
                    outdoor = outdoor,
                    coach = coach,
                    onStartOutdoor = onStartOutdoor,
                    onStopOutdoor = onStopOutdoor,
                    onDeleteOutdoor = onDeleteOutdoor,
                    onSaveTemplate = onSaveTemplate,
                    onDeleteTemplate = onDeleteTemplate,
                    onStartTemplate = onStartTemplate,
                    onGenerateWeek = onGenerateWeek,
                    onAcceptPlan = onAcceptPlan,
                    onDismissPlanSuggestion = onDismissPlanSuggestion,
                    onPlanSessionStatus = onPlanSessionStatus,
                    onStartPlannedSession = onStartPlannedSession,
                )
            }
            composable(Destination.HEALTH.route) {
                MyHealthScreen(
                    fasting = fasting,
                    onFastingEnable = onFastingEnable,
                    onFastingDisable = onFastingDisable,
                    onFastingStart = onFastingStart,
                    onFastingEnd = onFastingEnd,
                    onFastingCancel = onFastingCancel,
                    onFastingBackdate = onFastingBackdate,
                    onFastingDelete = onFastingDelete,
                    onFastingTick = onFastingTick,
                    healthConnect = healthConnect,
                    onToggleHealthCategory = onToggleHealthCategory,
                    onRequestHealthAccess = onRequestHealthAccess,
                    onImportHealth = onImportHealth,
                    onRefreshHealthAccess = onRefreshHealthAccess,
                    onAddManualEntry = onAddManualHealthEntry,
                    onDeleteManualEntry = onDeleteManualHealthEntry,
                    showHealthRationale = showHealthRationale,
                    onDismissHealthRationale = onDismissHealthRationale,
                )
            }
            composable(Destination.PROGRESS.route) {
                ProgressScreen(
                    workout = workout,
                    gymVisits = gymVisits,
                    fasting = fasting,
                    healthConnect = healthConnect,
                    onRefresh = onProgressRefresh,
                )
            }
            composable(Destination.PROFILE.route) {
                ProfileScreen(
                    driveStatus = driveStatus,
                    driveConnected = driveConnected,
                    driveDiagnosticStatus = driveDiagnosticStatus,
                    driveDiagnosticRunning = driveDiagnosticRunning,
                    onConnectDrive = onConnectDrive,
                    onDisconnectDrive = onDisconnectDrive,
                    onTestDrive = onTestDrive,
                )
            }
        }
    }
}
