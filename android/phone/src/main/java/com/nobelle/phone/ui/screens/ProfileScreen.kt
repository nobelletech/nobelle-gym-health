package com.nobelle.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.nobelle.core.coaching.ExerciseCatalog

/**
 * Profile (§3): goals, units, accessibility, connections, privacy, export,
 * account deletion and help, plus the honest media attribution list.
 */
@Composable
fun ProfileScreen(
    driveStatus: String,
    driveConnected: Boolean,
    driveDiagnosticStatus: String?,
    driveDiagnosticRunning: Boolean,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onTestDrive: () -> Unit,
) {
    var showMediaLicenses by remember { mutableStateOf(false) }
    if (showMediaLicenses) {
        MediaLicensesDialog(onDismiss = { showMediaLicenses = false })
    }
    ScreenColumn {
        ScreenTitle("Profile")

        InfoCard(
            title = "Google Drive",
            tone = CardTone.Sky,
            body = "$driveStatus. Nobelle requests only the app-data permission; health permissions stay local and separate.",
            trailing = {
                OutlinedButton(onClick = if (driveConnected) onDisconnectDrive else onConnectDrive) {
                    Text(if (driveConnected) "Disconnect" else "Connect")
                }
            },
        )
        if (driveConnected) {
            InfoCard(
                title = "Drive diagnostic",
                body = driveDiagnosticStatus ?: "Upload, verify and immediately delete one non-personal test event.",
                trailing = {
                    OutlinedButton(onClick = onTestDrive, enabled = !driveDiagnosticRunning) {
                        Text(if (driveDiagnosticRunning) "Testing…" else "Run test")
                    }
                },
            )
        }
        InfoCard("Goals & schedule", "General fitness, strength, endurance, mobility and more.")
        InfoCard("Units", "kg/lb, km/mi, mmol/L or mg/dL — conversions are explicit and tested.")
        InfoCard("Accessibility", "Large fonts, screen reader labels, reduced motion and colour-independent status.")
        InfoCard(
            title = "Privacy",
            body = "Your data syncs only to your own Google Drive. Export or delete anytime.",
            trailing = { OutlinedButton(onClick = {}) { Text("Manage") } },
        )
        InfoCard(
            title = "Media & licences",
            tone = CardTone.Sand,
            body = "Where every demonstration, animation and voice comes from — nothing is assumed licence-free.",
            trailing = { OutlinedButton(onClick = { showMediaLicenses = true }) { Text("View") } },
        )
        InfoCard("Help", "Runbook, diagnostics and support.")
    }
}

@Composable
private fun MediaLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media & licences") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttributionRow(
                    "Exercise instructions",
                    "Catalog text: ${ExerciseCatalog.REVIEWED_AUTHOR}.",
                )
                AttributionRow(
                    "Animation engine",
                    "Schematic stick figures, generated in-app — no licensed video content.",
                )
                AttributionRow(
                    "Narration",
                    "System text-to-speech — voice licences are governed by the voice engine " +
                        "installed on the device.",
                )
                AttributionRow(
                    "Video assets",
                    "None yet — pending content milestone. When added, every asset carries " +
                        "source, licence, attribution, version and a SHA-256 checksum in the media manifest.",
                )
                AttributionRow(
                    "Technique review",
                    "Movement demonstrations are marked “Technique review: pending” until " +
                        "a Nobelle reviewer signs them off.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AttributionRow(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}