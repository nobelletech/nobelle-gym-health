package com.nobelle.phone.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.nobelle.core.model.OutdoorSession
import com.nobelle.phone.outdoor.OutdoorTrackService
import com.nobelle.phone.outdoor.OutdoorViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Coach (§5): exercise library, filters, builder, weekly plan, guided session,
 * run/walk tracker, recovery sessions. Visually distinct from My Health.
 *
 * The Outdoor card records device-GPS tracks. Location is used only while a
 * user-started session records; permission is requested on the explicit
 * Start tap, never preemptively. No accuracy guarantees are implied — the
 * card reports exactly what the device fixes delivered (point count and the
 * accumulated distance between usable fixes).
 */
@Composable
fun CoachScreen(
    outdoor: OutdoorViewModel.OutdoorUiState,
    onStartOutdoor: (activity: String) -> Unit,
    onStopOutdoor: () -> Unit,
    onDeleteOutdoor: (String) -> Unit,
) {
    val context = LocalContext.current
    var showActivityPicker by remember { mutableStateOf(false) }

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

    ScreenColumn {
        ScreenTitle("Coach")

        InfoCard(
            title = "Exercise library",
            body = "Search by muscle, equipment and difficulty. Demonstrations show technique — they are not a live form assessment.",
        )

        InfoCard(
            title = "Weekly plan",
            body = "A suggested week appears here after onboarding. Every session is editable.",
            trailing = { OutlinedButton(onClick = {}) { Text("Calendar") } },
        )

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

private fun formatKm(meters: Double): String =
    if (meters >= 1_000) "%.2f km".format(meters / 1_000) else "${meters.toInt()} m"

private fun localDate(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
