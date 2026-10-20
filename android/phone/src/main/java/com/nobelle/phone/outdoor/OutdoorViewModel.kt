package com.nobelle.phone.outdoor

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.data.repository.OutdoorSessionRepository
import com.nobelle.core.model.OutdoorSession
import com.nobelle.core.model.OwnerId
import com.nobelle.phone.NobelleApplication
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Coach "Outdoor" state: mirrors the foreground service's live track and
 * persists the finished session through OutdoorSessionRepository, which also
 * enqueues the Drive sync event in the same transaction.
 */
class OutdoorViewModel(application: Application) : AndroidViewModel(application) {
    data class OutdoorUiState(
        val ownerAvailable: Boolean = false,
        val tracking: Boolean = false,
        val activity: String? = null,
        val elapsedSec: Long = 0,
        val distanceMeters: Double = 0.0,
        val pointCount: Int = 0,
        val sessions: List<OutdoorSession> = emptyList(),
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val repository: OutdoorSessionRepository? = try {
        OutdoorSessionRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }

    private val mutableState = MutableStateFlow(
        OutdoorUiState(message = if (repository == null) INIT_ERROR else null)
    )
    val state: StateFlow<OutdoorUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L
    private var persistingFinish = false

    init {
        viewModelScope.launch {
            OutdoorTrackStore.state.collect { track ->
                val finished = track.finished
                if (finished != null && !persistingFinish) {
                    persistingFinish = true
                    persistFinished(finished)
                    OutdoorTrackStore.clearFinished()
                    persistingFinish = false
                }
                mutableState.value = mutableState.value.copy(
                    tracking = track.tracking,
                    activity = track.activity,
                    distanceMeters = track.distanceMeters,
                    pointCount = track.points.size,
                    elapsedSec = track.startedAtMs
                        ?.let { maxOf(0L, (System.currentTimeMillis() - it) / 1_000) } ?: 0L,
                )
            }
        }
        // Elapsed ticker: refreshes the card once a second while recording.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val started = OutdoorTrackStore.state.value.startedAtMs
                if (OutdoorTrackStore.state.value.tracking && started != null) {
                    mutableState.value = mutableState.value.copy(
                        elapsedSec = maxOf(0L, (System.currentTimeMillis() - started) / 1_000)
                    )
                }
            }
        }
    }

    fun selectOwner(owner: OwnerId?) {
        val generation = ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        if (owner == null) {
            mutableState.value = OutdoorUiState(
                ownerAvailable = false,
                message = if (repository == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            mutableState.value = mutableState.value.copy(
                ownerAvailable = repository != null,
                sessions = emptyList(),
                message = if (repository == null) INIT_ERROR else null,
            )
        }
        val repo = repository ?: run {
            mutableState.value = mutableState.value.copy(ownerAvailable = false, message = INIT_ERROR)
            return
        }
        viewModelScope.launch {
            val sessions = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(ownerAvailable = true, sessions = sessions)
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts the foreground recorder. Call only after fine location is granted. */
    fun start(activity: String) {
        if (!hasLocationPermission()) {
            mutableState.value = mutableState.value.copy(
                message = "Location access is needed to record an outdoor session"
            )
            return
        }
        ContextCompat.startForegroundService(
            app,
            Intent(app, OutdoorTrackService::class.java)
                .setAction(OutdoorTrackService.ACTION_START)
                .putExtra(OutdoorTrackService.EXTRA_ACTIVITY, activity),
        )
    }

    /** Asks the service to stop; the finished track is persisted by the store collector. */
    fun stop() {
        app.startService(
            Intent(app, OutdoorTrackService::class.java).setAction(OutdoorTrackService.ACTION_STOP)
        )
    }

    fun deleteSession(id: String) {
        val owner = selectedOwner ?: return
        val repo = repository ?: return
        viewModelScope.launch {
            val message = try {
                repo.delete(owner, id)
                "Outdoor session deleted"
            } catch (failure: Exception) {
                failure.message?.takeIf { it.isNotBlank() } ?: "Outdoor session action failed"
            }
            val sessions = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(sessions = sessions, message = message)
        }
    }

    private suspend fun persistFinished(finished: OutdoorTrackStore.FinishedTrack) {
        val owner = selectedOwner
        val repo = repository
        val message = if (owner == null || repo == null) {
            "Outdoor session was not saved — no signed-in owner"
        } else {
            try {
                val now = Clock.System.now()
                repo.upsert(
                    owner,
                    OutdoorSession(
                        id = UUID.randomUUID().toString(),
                        ownerUID = owner.uid,
                        activity = finished.activity,
                        startedUtc = Instant.fromEpochMilliseconds(finished.startedAtMs),
                        endedUtc = Instant.fromEpochMilliseconds(finished.endedAtMs),
                        durationSec = maxOf(0L, (finished.endedAtMs - finished.startedAtMs) / 1_000),
                        distanceMeters = finished.distanceMeters,
                        route = finished.points,
                        pointCount = finished.points.size,
                        createdUtc = now,
                        revision = 1,
                    )
                )
                "Outdoor session saved"
            } catch (failure: Exception) {
                failure.message?.takeIf { it.isNotBlank() } ?: "Outdoor session was not saved"
            }
        }
        val sessions = if (owner != null && repo != null) {
            runCatching { repo.list(owner) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        mutableState.value = mutableState.value.copy(sessions = sessions, message = message)
    }

    private companion object {
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
