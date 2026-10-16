package com.nobelle.phone.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Today (§3): today's workout, start/resume, watch connection, recent sync,
 * activity summary, optional check-in. Health-derived suggestions only appear
 * after the user enables them.
 */
@Composable
fun TodayScreen() {
    ScreenColumn {
        ScreenTitle("Today")

        InfoCard(
            title = "No workout scheduled",
            body = "Connect your plan or pick a session to get started.",
            trailing = { Button(onClick = {}) { Text("Start") } },
        )

        InfoCard(
            title = "Watch",
            body = "Not paired. Pairing is optional — phone-only use is fully supported.",
            trailing = { OutlinedButton(onClick = {}) { Text("Pair") } },
        )

        InfoCard(
            title = "Sync",
            body = "Stored in your Google Drive. Sync starts after you connect your account.",
        )

        InfoCard(
            title = "Gym check-in",
            body = "One tap logs a visit — even without a recorded workout.",
            trailing = { OutlinedButton(onClick = {}) { Text("Log visit") } },
        )
    }
}
