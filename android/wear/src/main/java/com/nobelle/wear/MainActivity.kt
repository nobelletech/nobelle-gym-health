package com.nobelle.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Wear OS companion (§7): quick start, today's plan, live metrics, set logging,
 * rest timer and fasting check-in. Sessions persist locally and sync through
 * the enrolled phone — a Data Layer ack is not a cloud upload (§13).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { WearHome() }
        }
    }
}

@Composable
fun WearHome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nobelle", textAlign = TextAlign.Center)
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Quick start")
        }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Log gym visit")
        }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Fasting")
        }
        Text(
            "Waiting for phone plan sync",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
