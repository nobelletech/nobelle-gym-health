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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Wear shell (§7): hardware/runtime capability diagnostics plus timed workout
 * recording. A recorded workout is HMAC-signed with the enrollment token and
 * queued to the enrolled phone; it counts as delivered only after the phone
 * confirms receipt.
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
        // Deep link: opens the exercise preview directly (used by on-device
        // verification and future notifications/tiles).
        val openPreview = intent.getBooleanExtra(EXTRA_OPEN_PREVIEW, false)
        setContent {
            WearTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val enroll by model.enrollState.collectAsStateWithLifecycle()
                val enrollMessage by model.enrollMessage.collectAsStateWithLifecycle()
                val workout by model.workout.collectAsStateWithLifecycle()
                var showExercisePreview by remember { mutableStateOf(openPreview) }
                if (showExercisePreview) {
                    WearExercisePreview(onBack = { showExercisePreview = false })
                } else {
                    WearHome(
                        state = state,
                        enroll = enroll,
                        enrollMessage = enrollMessage,
                        workout = workout,
                        onStartWorkout = model::startWorkout,
                        onFinishWorkout = model::finishWorkout,
                        onDiscardWorkout = model::discardWorkout,
                        onSyncNow = model::syncNow,
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
                        onExercisePreview = { showExercisePreview = true },
                    )
                }
            }
        }
    }
}

@Composable
fun WearHome(
    state: WearViewModel.WearUiState,
    enroll: WatchEnrollState,
    enrollMessage: String?,
    workout: WearSyncCoordinator.WorkoutUiState,
    onStartWorkout: () -> Unit,
    onFinishWorkout: () -> Unit,
    onDiscardWorkout: () -> Unit,
    onSyncNow: () -> Unit,
    onEnroll: () -> Unit,
    onEnrollCancel: () -> Unit,
    onForgetPhone: () -> Unit,
    onRefresh: () -> Unit,
    onHeartRate: () -> Unit,
    onGps: () -> Unit,
    onHaptic: () -> Unit,
    onExercisePreview: () -> Unit,
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

        // Exercise media preview (§5): schematic figures + captions, the
        // same catalog as the phone (compact "preview set", honestly labelled).
        item {
            Button(onClick = onExercisePreview) { Text("Exercise preview") }
        }

        // Workout recording + phone sync (§7): only an enrolled watch can
        // deliver to the phone, so controls exist only while enrolled.
        if (enroll is WatchEnrollState.Enrolled) {
            if (workout.active) {
                item {
                    Text(
                        "Workout ${elapsedClock(workout.elapsedMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Text(
                        workout.latestBpm?.let { "${it.toInt()} bpm · live" }
                            ?: if (workout.hrSampleCount > 0) {
                                "Measuring heart rate…"
                            } else {
                                "No heart-rate samples yet"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = onFinishWorkout,
                        enabled = !workout.busy,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Finish", textAlign = TextAlign.Center) }
                }
                item {
                    Button(
                        onClick = onDiscardWorkout,
                        enabled = !workout.busy,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Discard", textAlign = TextAlign.Center) }
                }
            } else {
                item {
                    Button(
                        onClick = onStartWorkout,
                        enabled = !workout.busy,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Start workout", textAlign = TextAlign.Center) }
                }
            }
            if (workout.queued > 0) {
                item {
                    Text(
                        "${workout.queued} workout(s) queued for phone",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (workout.queued > 0) {
                item {
                    Button(
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) { Text("Sync now", textAlign = TextAlign.Center) }
                }
            }
            workout.message?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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

/** mm:ss ticking clock; grows to h:mm:ss past the hour. */
private fun elapsedClock(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** Intent extra: open the exercise preview on launch (verification deep link). */
const val EXTRA_OPEN_PREVIEW = "com.nobelle.wear.extra.OPEN_PREVIEW"
