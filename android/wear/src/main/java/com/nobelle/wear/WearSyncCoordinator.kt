package com.nobelle.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.awaitWithException
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.wearable.Wearable
import com.nobelle.core.model.ActiveWatchWorkout
import com.nobelle.core.model.EnrollCrypto
import com.nobelle.core.model.SyncPaths
import com.nobelle.core.model.WatchSyncAck
import com.nobelle.core.model.WatchSyncCodec
import com.nobelle.core.model.WatchSyncEventBody
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watch workout recording + phone sync (§7/§24). Owns the ACTIVE session
 * (persisted wrapped so it survives process death), best-effort live heart
 * rate while recording, the wrapped send queue, and the HMAC-authenticated
 * send to the enrolled phone over the Wear Data Layer.
 *
 * Singleton so the manifest-declared [WearEnrollListenerService] (ACKs) and
 * the UI share one session. Honest by construction: a workout counts as
 * delivered only after the phone's ACK deletes its queue entry.
 */
object WearSyncCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    data class WorkoutUiState(
        val active: Boolean = false,
        val elapsedMs: Long = 0L,
        val latestBpm: Double? = null,
        val hrSampleCount: Int = 0,
        val queued: Int = 0,
        val busy: Boolean = false,
        val message: String? = null,
    )

    private val mutableState = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = mutableState

    private var appContext: Context? = null
    private var activeStore: WatchActiveWorkoutStore? = null
    private var outbox: WatchOutboxStore? = null
    private var enrollments: WearEnrollmentStore? = null
    private var installationId: WearInstallationId? = null
    private var initialized = false

    private var active: ActiveWatchWorkout? = null
    private var ticker: Job? = null
    private var hrCallback: MeasureCallback? = null

    @Synchronized
    private fun ensureInit(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        activeStore = WatchActiveWorkoutStore(app)
        outbox = WatchOutboxStore(app)
        enrollments = WearEnrollmentStore(app)
        installationId = WearInstallationId(app)
        initialized = true
        // Restore an in-progress session after process death and resume the
        // elapsed timer — the record was persisted when it started.
        activeStore?.read()?.let { restored ->
            active = restored
            mutableState.value = mutableState.value.copy(
                active = true,
                elapsedMs = (System.currentTimeMillis() - restored.startedUtcMs)
                    .coerceAtLeast(0),
                hrSampleCount = restored.hrSampleCount,
            )
            startTicker()
            startHrCollectionIfPermitted()
        }
        refreshQueuedCount()
    }

    /** Called once from the view model; also drains the queue best-effort. */
    fun attach(context: Context) {
        ensureInit(context)
        syncNow(context)
    }

    /** User tapped "Start workout" — explicit session start, persisted first. */
    fun startWorkout(context: Context) {
        ensureInit(context)
        if (active != null || mutableState.value.busy) return
        if (enrollments?.read() == null) {
            mutableState.value =
                mutableState.value.copy(message = "Enroll with the phone before recording")
            return
        }
        val session = ActiveWatchWorkout(
            id = UUID.randomUUID().toString(),
            startedUtcMs = System.currentTimeMillis(),
        )
        val started = runCatching { activeStore?.write(session) }.isSuccess
        if (!started) {
            mutableState.value =
                mutableState.value.copy(message = "Could not start — storage write failed")
            return
        }
        active = session
        mutableState.value = WorkoutUiState(
            active = true,
            queued = mutableState.value.queued,
            message = if (heartRatePermitted()) {
                null
            } else {
                "Heart-rate permission not granted — recording duration only"
            },
        )
        startTicker()
        startHrCollectionIfPermitted()
    }

    /**
     * User tapped "Finish": the session becomes a queued sync event. The
     * operationUuid reuses the session id so an interrupted finish retried
     * later cannot produce a duplicate phone-side event.
     */
    fun finishWorkout(context: Context) {
        ensureInit(context)
        val session = active ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        scope.launch(Dispatchers.IO) {
            stopHrCollection()
            stopTicker()
            val endedMs = System.currentTimeMillis()
            val run = runCatching {
                val body = WatchSyncEventBody(
                    watchInstallationId = installationId!!.getOrCreate(),
                    operationUuid = session.id,
                    entityId = session.id,
                    entityType = SyncPaths.ENTITY_TYPE_WATCH_WORKOUT,
                    payloadJson = WatchSyncCodec.encodeWorkoutPayload(
                        session.toPayload(endedMs),
                    ),
                    utcEpochMs = endedMs,
                )
                outbox!!.enqueue(body)
                activeStore!!.clear()
            }
            if (run.isSuccess) {
                active = null
                mutableState.value = mutableState.value.copy(
                    active = false,
                    busy = false,
                    latestBpm = null,
                    hrSampleCount = 0,
                    elapsedMs = 0,
                    message = "Workout finished — queued for the phone",
                )
                refreshQueuedCount()
                syncNowInternal(context.applicationContext)
            } else {
                // Storage failed before the queue write committed — keep the
                // session ACTIVE rather than silently losing it.
                startTicker()
                startHrCollectionIfPermitted()
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    message = "Could not queue the workout — it is still active",
                )
            }
        }
    }

    /** User tapped "Discard": the session is deleted, nothing is queued. */
    fun discardWorkout(context: Context) {
        ensureInit(context)
        if (active == null || mutableState.value.busy) return
        stopHrCollection()
        stopTicker()
        activeStore?.clear()
        active = null
        mutableState.value = mutableState.value.copy(
            active = false,
            latestBpm = null,
            hrSampleCount = 0,
            elapsedMs = 0,
            message = "Workout discarded — nothing was sent",
        )
    }

    /** User tapped "Sync now": best-effort flush of the wrapped queue. */
    fun syncNow(context: Context) {
        ensureInit(context)
        scope.launch(Dispatchers.IO) { syncNowInternal(context.applicationContext) }
    }

    /**
     * Sends every queued event to the enrolled phone. The stored body is
     * re-tagged at send time with the current enrollment token, so entries
     * queued before a re-enrollment still authenticate. Files stay queued
     * until the phone's ACK removes them.
     */
    private suspend fun syncNowInternal(context: Context) {
        val queued = outbox?.list().orEmpty()
        refreshQueuedCount()
        if (queued.isEmpty()) return
        val enrollment = enrollments?.read()
        if (enrollment == null) {
            mutableState.value = mutableState.value.copy(
                message = "Not enrolled — ${queued.size} workout(s) kept on the watch",
            )
            return
        }
        val nodes = runCatching {
            Wearable.getNodeClient(context).connectedNodes.awaitTask()
        }.getOrNull().orEmpty()
        if (nodes.isEmpty()) {
            mutableState.value = mutableState.value.copy(
                message = "No connected phone — ${queued.size} workout(s) queued",
            )
            return
        }
        // Prefer the node that delivered the enrollment response; fall back
        // to every connected node — the HMAC tag gates acceptance either way.
        val targets = nodes.filter { it.id == enrollment.phoneNodeId }
            .ifEmpty { nodes }
        val token = EnrollCrypto.fromBase64(enrollment.tokenBase64)
        val client = Wearable.getMessageClient(context)
        var sent = 0
        for (entry in queued) {
            val body = runCatching { WatchSyncCodec.decodeBody(entry.bodyBytes) }
                .getOrNull()
            if (body == null) {
                // Undecryptable/corrupt — it can never be delivered.
                outbox?.delete(entry.operationUuid)
                continue
            }
            val wire = WatchSyncCodec.encodeEvent(token, body)
            val delivered = targets.any { node ->
                runCatching {
                    client.sendMessage(node.id, SyncPaths.EVENT, wire).awaitTask()
                }.isSuccess
            }
            if (delivered) sent++
        }
        refreshQueuedCount()
        val remaining = outbox?.count() ?: 0
        mutableState.value = mutableState.value.copy(
            message = when {
                sent == 0 ->
                    "Could not reach the phone — $remaining workout(s) still queued"
                remaining > 0 ->
                    "Sent to the phone — waiting for confirmation ($remaining queued)"
                else ->
                    "Sent to the phone — waiting for confirmation"
            },
        )
    }

    /** Called by the listener service when an ACK arrives: drop the entry. */
    internal fun onAck(context: Context, data: ByteArray) {
        ensureInit(context)
        val ack = runCatching { WatchSyncCodec.decodeAck(data) }.getOrNull() ?: return
        if (ack.schemaVersion != SyncPaths.SCHEMA_VERSION) return
        outbox?.delete(ack.operationUuid)
        refreshQueuedCount()
        val remaining = mutableState.value.queued
        mutableState.value = mutableState.value.copy(
            message = if (remaining == 0) {
                "Phone confirmed receipt of the queued workout(s)"
            } else {
                "Phone confirmed a workout — $remaining still queued"
            },
        )
    }

    // ---- live heart rate (best-effort, only while ACTIVE) ----

    private fun heartRatePermitted(): Boolean {
        val context = appContext ?: return false
        return WearHeartRatePermissions.forSdk(Build.VERSION.SDK_INT).all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Registers a MeasureClient callback for the session only, and only when
     * the runtime permission is already granted — a workout never triggers a
     * permission prompt. Without it the session records duration only.
     */
    private fun startHrCollectionIfPermitted() {
        val context = appContext ?: return
        if (!heartRatePermitted() || hrCallback != null) return
        val client = runCatching { HealthServices.getClient(context).measureClient }
            .getOrNull() ?: return
        val callback = object : MeasureCallback {
            override fun onRegistrationFailed(throwable: Throwable) {
                hrCallback = null
            }

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
            }

            override fun onDataReceived(data: DataPointContainer) {
                val points = data.getData(DataType.HEART_RATE_BPM)
                val session = active ?: return
                var updated = session
                var latest: Double? = null
                for (point in points) {
                    val bpm = point.value
                    if (bpm <= 0) continue
                    updated = updated.withHeartRateSample(bpm)
                    latest = bpm
                }
                if (latest == null) return
                active = updated
                // Persist the accumulators so process death loses nothing the
                // session already counted — the samples themselves are never
                // stored, only count/sum/max.
                runCatching { activeStore?.write(updated) }
                mutableState.value = mutableState.value.copy(
                    latestBpm = latest,
                    hrSampleCount = updated.hrSampleCount,
                )
            }
        }
        runCatching {
            client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        }.onSuccess { hrCallback = callback }
    }

    private fun stopHrCollection() {
        val callback = hrCallback ?: return
        hrCallback = null
        val context = appContext ?: return
        runCatching {
            HealthServices.getClient(context).measureClient
                .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val started = active?.startedUtcMs ?: break
                mutableState.value = mutableState.value.copy(
                    elapsedMs = (System.currentTimeMillis() - started)
                        .coerceAtLeast(0),
                )
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun refreshQueuedCount() {
        mutableState.value =
            mutableState.value.copy(queued = outbox?.count() ?: 0)
    }
}
