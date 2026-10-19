package com.nobelle.phone.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.data.repository.WorkoutRepository
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SetType
import com.nobelle.core.model.WatchWorkoutPayload
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import com.nobelle.phone.NobelleApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn

/**
 * Offline-first manual workout journey. Every mutation is persisted via
 * WorkoutRepository at the moment it happens; elapsed time derives from
 * stored UTC instants, so process death loses nothing.
 */
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    data class WorkoutUiState(
        val ownerAvailable: Boolean = false,
        val loading: Boolean = false,
        val session: WorkoutSession? = null,
        val sets: List<ExerciseSet> = emptyList(),
        val history: List<WorkoutPayload> = emptyList(),
        val watchWorkouts: List<WatchWorkoutPayload> = emptyList(),
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val repository: WorkoutRepository? = try {
        WorkoutRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }

    private val mutableState = MutableStateFlow(
        WorkoutUiState(message = if (repository == null) INIT_ERROR else null)
    )
    val state: StateFlow<WorkoutUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L

    fun selectOwner(owner: OwnerId?) {
        val generation = ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        if (owner == null) {
            mutableState.value = WorkoutUiState(
                ownerAvailable = false,
                message = if (repository == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            // Fully reset on owner switch so nothing in flight for the old
            // account can leave the new one visually stuck or mid-write.
            mutableState.value = WorkoutUiState(
                ownerAvailable = repository != null,
                loading = false,
                message = if (repository == null) INIT_ERROR else null,
            )
        }
        val repo = repository ?: run {
            mutableState.value = mutableState.value.copy(ownerAvailable = false, message = INIT_ERROR)
            return
        }
        viewModelScope.launch {
            val active = runCatching { repo.active(owner) }.getOrNull()
            val sets = active?.let { runCatching { repo.sets(owner, it.id) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val history = runCatching { repo.completedWithSets(owner) }
                .getOrDefault(emptyList())
                .take(HISTORY_LIMIT)
            val watch = runCatching { repo.watchWorkouts(owner) }
                .getOrDefault(emptyList())
                .take(HISTORY_LIMIT)
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(
                ownerAvailable = true,
                session = active,
                sets = sets,
                history = history,
                watchWorkouts = watch,
            )
        }
    }

    fun start(category: String = "Strength") = mutate { owner, repo ->
        val instant = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        repo.start(
            owner = owner,
            category = category,
            originDeviceId = app.installationId,
            startTimeZone = zone.id,
            sourceOffsetMinutes = instant.offsetIn(zone).totalSeconds / 60,
        )
        null
    }

    fun pauseOrResume() = mutate { owner, repo ->
        val session = currentSession() ?: return@mutate "No active workout to pause or resume"
        if (session.state == WorkoutState.ACTIVE) repo.pause(owner, session.id)
        else repo.resume(owner, session.id)
        null
    }

    fun addSet(
        exerciseId: String,
        reps: Int?,
        loadKg: Double?,
        durationSec: Int?,
        restSec: Int?,
        rpe: Double?,
        rir: Double?,
        tempo: String?,
        painFlag: Boolean,
        notes: String?,
        setType: SetType = SetType.STANDARD,
        groupId: String? = null,
        substitutedForExerciseId: String? = null,
    ) = mutate { owner, repo ->
        val session = currentSession() ?: return@mutate "No active workout"
        repo.addSet(
            owner, session.id, exerciseId, reps, loadKg, durationSec, restSec,
            rpe, rir, tempo, painFlag, notes, setType, groupId, substitutedForExerciseId,
        )
        null
    }

    fun finish() = mutate { owner, repo ->
        val session = currentSession() ?: return@mutate "No active workout to finish"
        repo.finish(owner, session.id)
        "Workout saved offline and queued for Drive sync"
    }

    fun discard() = mutate { owner, repo ->
        val session = currentSession() ?: return@mutate "No active workout to discard"
        repo.discard(owner, session.id)
        "Workout discarded locally"
    }

    private fun currentSession(): WorkoutSession? =
        mutableState.value.session?.takeIf {
            it.state == WorkoutState.ACTIVE || it.state == WorkoutState.PAUSED
        }

    private fun mutate(block: suspend (OwnerId, WorkoutRepository) -> String?) {
        val owner = selectedOwner ?: return
        val repo = repository ?: run {
            mutableState.value = mutableState.value.copy(message = INIT_ERROR)
            return
        }
        if (mutableState.value.loading) return
        val generation = selectionGeneration
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val message = try {
                block(owner, repo)
            } catch (failure: Exception) {
                failure.message?.takeIf { it.isNotBlank() } ?: "Workout action failed"
            }
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            val session = runCatching { repo.active(owner) }.getOrNull()
            val sets = session?.let { runCatching { repo.sets(owner, it.id) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val history = runCatching { repo.completedWithSets(owner) }
                .getOrDefault(emptyList())
                .take(HISTORY_LIMIT)
            val watch = runCatching { repo.watchWorkouts(owner) }
                .getOrDefault(emptyList())
                .take(HISTORY_LIMIT)
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(
                loading = false,
                session = session,
                sets = sets,
                history = history,
                watchWorkouts = watch,
                message = message,
            )
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 50
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
