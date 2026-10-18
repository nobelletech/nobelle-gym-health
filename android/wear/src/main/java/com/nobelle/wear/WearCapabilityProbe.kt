package com.nobelle.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.health.services.client.HealthServices
import androidx.health.services.client.awaitWithException
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One-shot hardware/runtime capability report (M0): what the watch actually
 * reports, never what marketing claims. `error` distinguishes a failed probe
 * from a real "unsupported" — the UI never upgrades a failure into a verdict.
 */
data class WearCapabilities(
    val model: String,
    val sdkInt: Int,
    val healthServices: Boolean,
    val measureHeartRate: Boolean,
    val exerciseHeartRate: Boolean,
    val runningSupported: Boolean,
    val runningLocation: Boolean,
    val gpsFeature: Boolean,
    val vibrator: Boolean,
    val phoneNodes: Int?,
    val error: String? = null,
)

/**
 * Heart-rate runtime permission per API level (pure, testable): API 36 /
 * Wear OS 6 uses the granular health permission; older releases use
 * BODY_SENSORS — the two are never requested together.
 */
object WearHeartRatePermissions {
    fun forSdk(sdkInt: Int): Array<String> =
        if (sdkInt >= 36) {
            arrayOf("android.permission.health.READ_HEART_RATE")
        } else {
            arrayOf(android.Manifest.permission.BODY_SENSORS)
        }
}

/** The only location permission the explicit GPS diagnostic needs. */
object WearLocationPermission {
    const val REQUIRED: String = android.Manifest.permission.ACCESS_FINE_LOCATION
}

/** Display text for the explicit GPS-fix diagnostic — pure for unit tests. */
object WearGpsLabels {
    fun gpsFix(accuracyM: Float?): String =
        accuracyM?.let { "GPS fix acquired · accuracy about ${it.roundToInt()}m" }
            ?: "GPS fix acquired"
}

/** Display labels for probe rows — pure so unit tests cover the wording. */
object WearProbeLabels {
    const val SUPPORTED = "Supported"
    const val NOT_REPORTED = "Not reported"
    const val PROBE_FAILED = "Probe failed"

    fun label(supported: Boolean, probeFailed: Boolean): String =
        when {
            probeFailed -> PROBE_FAILED
            supported -> SUPPORTED
            else -> NOT_REPORTED
        }

    fun healthRow(capabilities: WearCapabilities, supported: Boolean): String =
        label(supported, probeFailed = capabilities.error != null)

    fun nodes(phoneNodes: Int?): String =
        phoneNodes?.let { "$it reachable node${if (it == 1) "" else "s"}" } ?: PROBE_FAILED
}

object WearCapabilityProbe {
    suspend fun probe(context: Context): WearCapabilities {
        var healthError: String? = null
        var healthServices = false
        var measureHeartRate = false
        var exerciseHeartRate = false
        var runningSupported = false
        var runningLocation = false
        try {
            val client = HealthServices.getClient(context)
            val measure = client.measureClient.getCapabilitiesAsync()
                .awaitWithException()
            healthServices = true
            measureHeartRate =
                DataType.HEART_RATE_BPM in measure.supportedDataTypesMeasure
            val exercise = client.exerciseClient.getCapabilitiesAsync()
                .awaitWithException()
            val running = exercise.typeToCapabilities[ExerciseType.RUNNING]
            runningSupported = running != null
            exerciseHeartRate =
                running?.let { DataType.HEART_RATE_BPM in it.supportedDataTypes } == true
            runningLocation =
                running?.let { DataType.LOCATION in it.supportedDataTypes } == true
        } catch (failure: Exception) {
            // A failed probe is reported as such — never presented as
            // "unsupported", which would be a claim we cannot make.
            healthError =
                listOfNotNull(failure::class.simpleName, failure.message)
                    .joinToString(": ")
        }
        val nodes = try {
            Wearable.getNodeClient(context).connectedNodes.awaitTask().size
        } catch (failure: Exception) {
            null // null = unknown, not zero
        }
        return WearCapabilities(
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            healthServices = healthServices,
            measureHeartRate = measureHeartRate,
            exerciseHeartRate = exerciseHeartRate,
            runningSupported = runningSupported,
            runningLocation = runningLocation,
            gpsFeature = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            vibrator = hasVibrator(context),
            phoneNodes = nodes,
            error = healthError,
        )
    }

    private fun hasVibrator(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator?.hasVibrator() == true
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                ?.hasVibrator() == true
        }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
