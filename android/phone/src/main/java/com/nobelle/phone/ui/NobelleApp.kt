package com.nobelle.phone.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobelle.core.health.HealthConnectCategory
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
import com.nobelle.phone.workout.WorkoutViewModel

// Primary navigation (§3): Today | Coach | My Health | Progress | Profile
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    COACH("coach", "Coach", Icons.Filled.Star),
    HEALTH("health", "My Health", Icons.Filled.Favorite),
    PROGRESS("progress", "Progress", Icons.Filled.DateRange),
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
    onStartOutdoor: (String) -> Unit,
    onStopOutdoor: () -> Unit,
    onDeleteOutdoor: (String) -> Unit,
    onProgressRefresh: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        }
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
                    onStartOutdoor = onStartOutdoor,
                    onStopOutdoor = onStopOutdoor,
                    onDeleteOutdoor = onDeleteOutdoor,
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
