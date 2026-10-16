package com.nobelle.phone.ui.screens

import androidx.compose.runtime.Composable

/**
 * Progress (§3): training charts and personal bests, kept separate from
 * medical interpretation.
 */
@Composable
fun ProgressScreen() {
    ScreenColumn {
        ScreenTitle("Progress")

        InfoCard("Strength & volume", "Charts appear after your first logged session.")
        InfoCard("Running & walking", "Pace, distance and consistency trends.")
        InfoCard("Personal bests", "Automatically tracked from confirmed sets.")
        InfoCard("Attendance", "Gym days, home and outdoor sessions on one calendar.")
        InfoCard("Reports", "Weekly and monthly summaries — descriptive, not medical advice.")
    }
}
