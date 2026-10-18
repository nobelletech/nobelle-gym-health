package com.nobelle.phone.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.data.repository.GymVisitRepository
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.OwnerId
import com.nobelle.phone.NobelleApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Offline-first gym attendance. Every mutation is persisted via
 * GymVisitRepository at the moment it happens; attendance days are distinct
 * local dates only — linked workouts never affect the count.
 */
class GymVisitViewModel(application: Application) : AndroidViewModel(application) {
    data class GymVisitUiState(
        val ownerAvailable: Boolean = false,
        val loading: Boolean = false,
        val visits: List<GymVisit> = emptyList(),
        val attendanceDays: Int = 0,
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val repository: GymVisitRepository? = try {
        GymVisitRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }

    private val mutableState = MutableStateFlow(
        GymVisitUiState(message = if (repository == null) INIT_ERROR else null)
    )
    val state: StateFlow<GymVisitUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L

    fun selectOwner(owner: OwnerId?) {
        val generation = ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        if (owner == null) {
            mutableState.value = GymVisitUiState(
                ownerAvailable = false,
                message = if (repository == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            // Fully reset on owner switch so nothing in flight for the old
            // account can leave the new one visually stuck or mid-write.
            mutableState.value = GymVisitUiState(
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
            val visits = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(
                ownerAvailable = true,
                visits = visits,
                attendanceDays = attendanceDays(visits),
            )
        }
    }

    fun logNow(gymLabel: String?, category: String?, notes: String?) = mutate { owner, repo ->
        repo.create(
            owner = owner,
            start = Clock.System.now(),
            originalTimeZone = TimeZone.currentSystemDefault().id,
            gymLabel = gymLabel,
            category = category,
            notes = notes,
        )
        "Gym visit logged offline"
    }

    fun addBackdated(input: GymVisitInput) = mutate { owner, repo ->
        repo.create(
            owner = owner,
            start = input.start,
            originalTimeZone = input.zoneId,
            gymLabel = input.gymLabel,
            category = input.category,
            notes = input.notes,
        )
        "Gym visit logged offline"
    }

    fun endVisit(id: String) = mutate { owner, repo ->
        repo.end(owner, id, Clock.System.now())
        "Gym visit ended"
    }

    fun editVisit(id: String, input: GymVisitInput) = mutate { owner, repo ->
        repo.edit(
            owner = owner,
            id = id,
            start = input.start,
            originalTimeZone = input.zoneId,
            gymLabel = input.gymLabel,
            category = input.category,
            notes = input.notes,
        )
        "Gym visit updated"
    }

    fun deleteVisit(id: String) = mutate { owner, repo ->
        repo.delete(owner, id)
        "Gym visit deleted"
    }

    private fun mutate(block: suspend (OwnerId, GymVisitRepository) -> String?) {
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
                failure.message?.takeIf { it.isNotBlank() } ?: "Gym visit action failed"
            }
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            val visits = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(
                loading = false,
                visits = visits,
                attendanceDays = attendanceDays(visits),
                message = message,
            )
        }
    }

    private companion object {
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"

        fun attendanceDays(visits: List<GymVisit>): Int =
            visits.map { it.localAttendanceDate }.distinct().size
    }
}
