package com.nobelle.phone.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Coach (§5): exercise library, filters, builder, weekly plan, guided session,
 * run/walk tracker, recovery sessions. Visually distinct from My Health.
 */
@Composable
fun CoachScreen() {
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
            body = "Run, walk and cycling tracking with GPS routes. Location is used only when you start an outdoor session.",
            trailing = { OutlinedButton(onClick = {}) { Text("Start") } },
        )
    }
}
