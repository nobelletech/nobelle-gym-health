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
import com.nobelle.phone.ui.screens.CoachScreen
import com.nobelle.phone.ui.screens.MyHealthScreen
import com.nobelle.phone.ui.screens.ProfileScreen
import com.nobelle.phone.ui.screens.ProgressScreen
import com.nobelle.phone.ui.screens.TodayScreen

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
            composable(Destination.TODAY.route) { TodayScreen() }
            composable(Destination.COACH.route) { CoachScreen() }
            composable(Destination.HEALTH.route) { MyHealthScreen() }
            composable(Destination.PROGRESS.route) { ProgressScreen() }
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
