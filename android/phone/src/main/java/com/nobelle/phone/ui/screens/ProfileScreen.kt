package com.nobelle.phone.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Profile (§3): goals, units, accessibility, connections, privacy, export,
 * account deletion and help.
 */
@Composable
fun ProfileScreen(
    driveStatus: String,
    driveConnected: Boolean,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
) {
    ScreenColumn {
        ScreenTitle("Profile")

        InfoCard(
            title = "Google Drive",
            body = "$driveStatus. Nobelle requests only the app-data permission; health permissions stay local and separate.",
            trailing = {
                OutlinedButton(onClick = if (driveConnected) onDisconnectDrive else onConnectDrive) {
                    Text(if (driveConnected) "Disconnect" else "Connect")
                }
            },
        )
        InfoCard("Goals & schedule", "General fitness, strength, endurance, mobility and more.")
        InfoCard("Units", "kg/lb, km/mi, mmol/L or mg/dL — conversions are explicit and tested.")
        InfoCard("Accessibility", "Large fonts, screen reader labels, reduced motion and colour-independent status.")
        InfoCard(
            title = "Privacy",
            body = "Your data syncs only to your own Google Drive. Export or delete anytime.",
            trailing = { OutlinedButton(onClick = {}) { Text("Manage") } },
        )
        InfoCard("Help", "Runbook, diagnostics and support.")
    }
}
