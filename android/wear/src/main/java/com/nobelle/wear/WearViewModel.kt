package com.nobelle.wear

import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.awaitWithException
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wear shell: capability probe + explicit user-triggered live tests.
 * Nothing is recorded, persisted or synced — a shown bpm is a live sample
 * only, and a reachable phone node is not enrollment.
 */
class WearViewModel(application: Application) : AndroidViewModel(application) {
    data class WearUiState(
        val capabilities: WearCapabilities? = null,
        val probing: Boolean = false,
        val heartRateGranted: Boolean = false,
        val testingHeartRate: Boolean = false,
        val latestHeartRateBpm: Double? = null,
        val locationGranted: Boolean = false,
        val testingGps: Boolean = false,
        val gpsFixReceived: Boolean = false,
        val gpsFixAccuracyM: Float? = null,
        val message: String? = null,
    )

    private val mutableState = MutableStateFlow(WearUiState())
    val state: StateFlow<WearUiState> = mutableState

    init {
        probe()
    }

    fun probe() {
        if (mutableState.value.probing) return
        mutableState.value = mutableState.value.copy(probing = true, message = null)
        viewModelScope.launch {
            val capabilities = runCatching {
                WearCapabilityProbe.probe(getApplication())
            }.getOrElse { failure ->
                WearCapabilities(
                    model = Build.MODEL,
                    sdkInt = Build.VERSION.SDK_INT,
                    healthServices = false,
                    measureHeartRate = false,
                    exerciseHeartRate = false,
                    runningSupported = false,
                    runningLocation = false,
                    gpsFeature = false,
                    vibrator = false,
                    phoneNodes = null,
                    error = listOfNotNull(failure::class.simpleName, failure.message)
                        .joinToString(": "),
                )
            }
            mutableState.value = mutableState.value.copy(
                probing = false,
                capabilities = capabilities,
                heartRateGranted = hasHeartRatePermission(),
                locationGranted = hasLocationPermission(),
            )
        }
    }

    /** Permission needed for the explicit live HR test on this API level. */
    fun requiredHeartRatePermissions(): Array<String> =
        WearHeartRatePermissions.forSdk(Build.VERSION.SDK_INT)

    fun hasHeartRatePermission(): Boolean =
        requiredHeartRatePermissions().all {
            getApplication<Application>().checkSelfPermission(it) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun onSensorPermissionResult(grants: Map<String, Boolean>) {
        val granted = requiredHeartRatePermissions().all { grants[it] == true }
        mutableState.value = mutableState.value.copy(heartRateGranted = granted)
        if (granted) {
            testHeartRate()
        } else {
            showMessage("Heart-rate permission denied — live test did not run")
        }
    }

    /** Live HR sample for the explicit test only — never stored or sent. */
    fun testHeartRate() {
        if (mutableState.value.testingHeartRate) return
        if (!hasHeartRatePermission()) {
            showMessage("Grant heart-rate access first")
            return
        }
        mutableState.value = mutableState.value.copy(
            testingHeartRate = true,
            latestHeartRateBpm = null,
            message = null,
        )
        viewModelScope.launch {
            val client = try {
                HealthServices.getClient(getApplication()).measureClient
            } catch (failure: Exception) {
                finishHeartRateTest(null, "Heart-rate test could not start")
                return@launch
            }
            val sample = CompletableDeferred<Double>()
            val callback = object : MeasureCallback {
                override fun onRegistrationFailed(throwable: Throwable) {
                    sample.completeExceptionally(throwable)
                }

                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability,
                ) {
                }

                override fun onDataReceived(data: DataPointContainer) {
                    data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
                        ?.takeIf { it > 0 }
                        ?.let { sample.complete(it) }
                }
            }
            try {
                client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                val bpm = withTimeoutOrNull(LIVE_TEST_TIMEOUT_MS) { sample.await() }
                finishHeartRateTest(
                    bpm,
                    if (bpm == null) "No heart-rate sample received in 30 seconds" else null,
                )
            } catch (failure: Exception) {
                finishHeartRateTest(null, "Heart-rate test failed")
            } finally {
                runCatching {
                    client.unregisterMeasureCallbackAsync(
                        DataType.HEART_RATE_BPM, callback,
                    ).awaitWithException()
                }
            }
        }
    }

    private fun finishHeartRateTest(bpm: Double?, message: String?) {
        mutableState.value = mutableState.value.copy(
            testingHeartRate = false,
            latestHeartRateBpm = bpm,
            message = message,
        )
    }

    fun requiredLocationPermission(): Array<String> =
        arrayOf(WearLocationPermission.REQUIRED)

    fun hasLocationPermission(): Boolean =
        requiredLocationPermission().all {
            getApplication<Application>().checkSelfPermission(it) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun onLocationPermissionResult(granted: Boolean) {
        mutableState.value = mutableState.value.copy(locationGranted = granted)
        if (granted) {
            testGps()
        } else {
            showMessage("Location permission denied — GPS test did not run")
        }
    }

    /**
     * Explicit one-shot GPS acquisition check via Android LocationManager —
     * not Health Services, not workout recording. Coordinates are never
     * read, stored, logged or sent; only the reported accuracy is kept.
     */
    fun testGps() {
        if (mutableState.value.testingGps) return
        if (!hasLocationPermission()) {
            showMessage("Grant location access first")
            return
        }
        mutableState.value = mutableState.value.copy(
            testingGps = true,
            gpsFixReceived = false,
            gpsFixAccuracyM = null,
            message = null,
        )
        viewModelScope.launch {
            val context = getApplication<Application>()
            val manager = context.getSystemService(LocationManager::class.java)
            if (manager == null ||
                !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                finishGpsTest("GPS provider is disabled")
                return@launch
            }
            val fix = CompletableDeferred<Float?>()
            val listener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    // Only the reported accuracy is captured — never lat/lng/alt.
                    fix.complete(location.accuracy.takeIf { location.hasAccuracy() })
                }
            }
            try {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f,
                    context.mainExecutor, listener,
                )
                withTimeoutOrNull(GPS_TEST_TIMEOUT_MS) { fix.await() }
                if (fix.isCompleted) {
                    mutableState.value = mutableState.value.copy(
                        testingGps = false,
                        gpsFixReceived = true,
                        gpsFixAccuracyM = fix.getCompleted(),
                    )
                } else {
                    finishGpsTest(
                        "No GPS fix received in 60 seconds — " +
                            "try outdoors with a clear sky view",
                    )
                }
            } catch (failure: SecurityException) {
                finishGpsTest("Location permission not granted")
            } catch (failure: Exception) {
                finishGpsTest("GPS test failed")
            } finally {
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }

    private fun finishGpsTest(message: String) {
        mutableState.value = mutableState.value.copy(
            testingGps = false,
            message = message,
        )
    }

    /** One-shot haptic pulse on explicit tap only — no background vibration. */
    fun testHaptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>()
                .getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>()
                .getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            showMessage("No vibrator reported")
            return
        }
        vibrator.vibrate(
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE),
        )
        showMessage("Haptic pulse sent")
    }

    fun showMessage(message: String) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    private companion object {
        const val LIVE_TEST_TIMEOUT_MS = 30_000L
        const val GPS_TEST_TIMEOUT_MS = 60_000L
    }
}
