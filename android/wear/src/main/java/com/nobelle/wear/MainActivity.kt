package com.nobelle.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Wear shell (§7): hardware/runtime capability diagnostic only. There is no
 * workout recording, persistence or phone sync yet — the UI says so.
 */
class MainActivity : ComponentActivity() {
    private val model by viewModels<WearViewModel>()

    private val sensorPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> model.onSensorPermissionResult(grants) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> model.onLocationPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by model.state.collectAsStateWithLifecycle()
                WearHome(
                    state = state,
                    onRefresh = model::probe,
                    onHeartRate = {
                        if (model.hasHeartRatePermission()) {
                            model.testHeartRate()
                        } else {
                            sensorPermissionLauncher.launch(
                                model.requiredHeartRatePermissions(),
                            )
                        }
                    },
                    onGps = {
                        if (model.hasLocationPermission()) {
                            model.testGps()
                        } else {
                            locationPermissionLauncher.launch(
                                model.requiredLocationPermission().first(),
                            )
                        }
                    },
                    onHaptic = model::testHaptic,
                )
            }
        }
    }
}

@Composable
fun WearHome(
    state: WearViewModel.WearUiState,
    onRefresh: () -> Unit,
    onHeartRate: () -> Unit,
    onGps: () -> Unit,
    onHaptic: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        item { Text("Nobelle", textAlign = TextAlign.Center) }

        val caps = state.capabilities
        if (caps != null) {
            item {
                Text(
                    "${caps.model} · API ${caps.sdkInt}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                ProbeRow("Health Services", WearProbeLabels.healthRow(caps, caps.healthServices))
            }
            item {
                ProbeRow("Measure heart rate", WearProbeLabels.healthRow(caps, caps.measureHeartRate))
            }
            item {
                ProbeRow("Exercise heart rate", WearProbeLabels.healthRow(caps, caps.exerciseHeartRate))
            }
            item {
                ProbeRow("Running exercise", WearProbeLabels.healthRow(caps, caps.runningSupported))
            }
            item {
                ProbeRow("GPS hardware", WearProbeLabels.label(caps.gpsFeature, probeFailed = false))
            }
            item {
                ProbeRow("Running location", WearProbeLabels.healthRow(caps, caps.runningLocation))
            }
            item {
                ProbeRow("Haptics", WearProbeLabels.label(caps.vibrator, probeFailed = false))
            }
            item { ProbeRow("Phone nodes", WearProbeLabels.nodes(caps.phoneNodes)) }
            caps.error?.let { error ->
                item {
                    Text(
                        "Probe failed: $error",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item {
                Text(
                    if (state.probing) "Probing…" else "No capability report yet",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        state.latestHeartRateBpm?.let { bpm ->
            item {
                Text(
                    "${bpm.toInt()} bpm\nLive test only — not saved",
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (state.gpsFixReceived) {
            item {
                Text(
                    "${WearGpsLabels.gpsFix(state.gpsFixAccuracyM)}\n" +
                        "Location was not saved",
                    textAlign = TextAlign.Center,
                )
            }
        }
        state.message?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            Button(onClick = onRefresh, enabled = !state.probing) {
                Text("Refresh")
            }
        }
        item {
            Button(
                onClick = onHeartRate,
                enabled = !state.testingHeartRate,
            ) {
                Text(
                    if (state.heartRateGranted) {
                        if (state.testingHeartRate) "Testing…" else "Test live HR"
                    } else {
                        "Grant & test HR"
                    },
                )
            }
        }
        item {
            Button(
                onClick = onGps,
                enabled = !state.testingGps,
            ) {
                Text(
                    if (state.locationGranted) {
                        if (state.testingGps) "Testing GPS…" else "Test GPS fix"
                    } else {
                        "Grant & test GPS"
                    },
                )
            }
        }
        item {
            Button(onClick = onHaptic) { Text("Test haptic") }
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Quick start", textAlign = TextAlign.Center)
            }
        }
        item {
            Text(
                "Workout recording not implemented yet\nPhone enrollment not implemented",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProbeRow(name: String, status: String) {
    Text(
        "$name: $status",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}
