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
import com.nobelle.core.model.WatchEnrollState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
            WearTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val enroll by model.enrollState.collectAsStateWithLifecycle()
                val enrollMessage by model.enrollMessage.collectAsStateWithLifecycle()
                WearHome(
                    state = state,
                    enroll = enroll,
                    enrollMessage = enrollMessage,
                    onEnroll = model::enrollWatch,
                    onEnrollCancel = model::cancelEnroll,
                    onForgetPhone = model::forgetPhone,
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
    enroll: WatchEnrollState,
    enrollMessage: String?,
    onEnroll: () -> Unit,
    onEnrollCancel: () -> Unit,
    onForgetPhone: () -> Unit,
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
        item {
            Text(
                "Nobelle",
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

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
                "Workout recording not implemented yet",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        // Phone enrollment (§7): the 6-digit code is the only thing shown —
        // the derived enrollment token is never displayed or stored in
        // plaintext.
        when (val enrollState = enroll) {
            is WatchEnrollState.Enrolled -> {
                val enrolledDate = enrollState.enrolledUtc
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
                item {
                    Text(
                        "Enrolled with phone\nsince $enrolledDate",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = onForgetPhone,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Forget phone", textAlign = TextAlign.Center) }
                }
            }
            is WatchEnrollState.Waiting -> {
                item {
                    Text(
                        enrollState.code,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Text(
                        "Confirm this code on the phone",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = onEnrollCancel,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Cancel", textAlign = TextAlign.Center) }
                }
            }
            WatchEnrollState.NotEnrolled -> {
                item {
                    Button(
                        onClick = onEnroll,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Enroll watch", textAlign = TextAlign.Center) }
                }
                item {
                    Text(
                        "Pairing is optional — it needs the Nobelle phone app",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        enrollMessage?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
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
