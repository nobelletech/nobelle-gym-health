package com.nobelle.phone.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nobelle.core.model.MarkerGroup

/**
 * My Health (§3/§10): overview cards by group; each marker opens a dedicated
 * history screen with units, range, source, last update, gaps and export.
 */
@Composable
fun MyHealthScreen() {
    ScreenColumn {
        ScreenTitle("My Health")

        MarkerGroup.entries.forEach { group ->
            InfoCard(
                title = group.displayName,
                body = "No data yet. Connect a source or add a manual entry.",
            )
        }

        InfoCard(
            title = "Fasting",
            body = "Optional eating-window tracker. Enable it in settings — no schedule is started for you.",
            trailing = { OutlinedButton(onClick = {}) { Text("Set up") } },
        )

        InfoCard(
            title = "Connections & Data",
            body = "Watch, Health Connect sources, cloud sync choices and errors.",
            trailing = { OutlinedButton(onClick = {}) { Text("Manage") } },
        )

        InfoCard(
            title = "Ask Nobelle Health AI",
            body = "On-device explanations of your records. Disabled until you enable AI processing.",
            trailing = { OutlinedButton(onClick = {}) { Text("About") } },
        )
    }
}

private val MarkerGroup.displayName: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
