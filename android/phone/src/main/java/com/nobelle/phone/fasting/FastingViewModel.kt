package com.nobelle.phone.fasting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.data.repository.FastingScheduleRepository
import com.nobelle.core.data.repository.FastingSessionRepository
import com.nobelle.core.model.FastingSchedule
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.OwnerId
import com.nobelle.phone.NobelleApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Optional fasting tracker. The enabled schedule is planning context only —
 * nothing auto-starts or auto-ends; the timer derives from persisted instants
 * so process death loses nothing and no service runs.
 */
class FastingViewModel(application: Application) : AndroidViewModel(application) {
    data class FastingUiState(
        val ownerAvailable: Boolean = false,
        val loading: Boolean = false,
        val schedule: FastingSchedule? = null,
        val active: FastingSession? = null,
        val history: List<FastingSession> = emptyList(),
        val now: Instant = Clock.System.now(),
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val repositories: Pair<FastingSessionRepository, FastingScheduleRepository>? = try {
        FastingSessionRepository(app.database, app.installationId) to
            FastingScheduleRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }

    private val mutableState = MutableStateFlow(
        FastingUiState(message = if (repositories == null) INIT_ERROR else null)
    )
    val state: StateFlow<FastingUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L

    fun selectOwner(owner: OwnerId?) {
        val generation = ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        if (owner == null) {
            mutableState.value = FastingUiState(
                ownerAvailable = false,
                message = if (repositories == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            // Fully reset on owner switch so nothing in flight for the old
            // account can leave the new one visually stuck or mid-write.
            mutableState.value = FastingUiState(
                ownerAvailable = repositories != null,
                loading = false,
                message = if (repositories == null) INIT_ERROR else null,
            )
        }
        val (sessions, schedules) = repositories ?: run {
            mutableState.value = mutableState.value.copy(ownerAvailable = false, message = INIT_ERROR)
            return
        }
        viewModelScope.launch {
            val snapshot = runCatching { load(sessions, schedules, owner) }.getOrNull()
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            if (snapshot != null) {
                mutableState.value = mutableState.value.copy(
                    ownerAvailable = true,
                    schedule = snapshot.schedule,
                    active = snapshot.active,
                    history = snapshot.history,
                )
            }
        }
    }

    fun enable(fastingHours: Int, startLocalTime: String) = mutate { owner, repos ->
        repos.second.enable(owner, fastingHours, startLocalTime)
        "Fasting tracker enabled"
    }

    fun disable() = mutate { owner, repos ->
        repos.second.disable(owner)
        "Fasting tracker disabled"
    }

    fun startNow() = mutate { owner, repos ->
        val schedule = repos.second.getEnabled(owner)
            ?: return@mutate "Enable a fasting schedule first"
        val zone = TimeZone.currentSystemDefault()
        repos.first.start(
            owner = owner,
            start = Clock.System.now(),
            startZone = zone.id,
            targetDurationSec = schedule.fastingHours.toLong() * 3600,
            scheduleId = schedule.id,
        )
        "Fast started"
    }

    fun endNow() = mutate { owner, repos ->
        val active = mutableState.value.active ?: return@mutate "No fast is active"
        repos.first.end(owner, active.id, Clock.System.now(), TimeZone.currentSystemDefault().id)
        "Fast ended"
    }

    fun cancelActive() = mutate { owner, repos ->
        val active = mutableState.value.active ?: return@mutate "No fast is active"
        repos.first.cancel(owner, active.id, Clock.System.now(), TimeZone.currentSystemDefault().id)
        "Fast cancelled"
    }

    fun addBackdated(input: FastingBackdateInput) = mutate { owner, repos ->
        val schedule = repos.second.getEnabled(owner)
        val targetSec = schedule?.fastingHours?.toLong()?.times(3600)
            ?: (input.end - input.start).inWholeSeconds
        repos.first.createCompleted(
            owner = owner,
            start = input.start,
            end = input.end,
            startZone = input.startZone,
            endZone = input.endZone,
            targetDurationSec = targetSec,
            scheduleId = schedule?.id,
            notes = input.notes,
        )
        "Past fast added"
    }

    fun deleteSession(id: String) = mutate { owner, repos ->
        repos.first.delete(owner, id)
        "Fast deleted"
    }

    /** Re-render elapsed time. Never touches storage or session state. */
    fun tick() {
        if (mutableState.value.active == null) return
        mutableState.value = mutableState.value.copy(now = Clock.System.now())
    }

    private class Snapshot(
        val schedule: FastingSchedule?,
        val active: FastingSession?,
        val history: List<FastingSession>,
    )

    private suspend fun load(
        sessions: FastingSessionRepository,
        schedules: FastingScheduleRepository,
        owner: OwnerId,
    ): Snapshot {
        val active = sessions.active(owner)
        return Snapshot(
            schedule = schedules.getEnabled(owner),
            active = active,
            history = sessions.list(owner)
                .filter { it.status != FastingSession.Status.ACTIVE },
        )
    }

    private fun mutate(
        block: suspend (OwnerId, Pair<FastingSessionRepository, FastingScheduleRepository>) -> String?,
    ) {
        val owner = selectedOwner ?: return
        val repos = repositories ?: run {
            mutableState.value = mutableState.value.copy(message = INIT_ERROR)
            return
        }
        if (mutableState.value.loading) return
        val generation = selectionGeneration
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val message = try {
                block(owner, repos)
            } catch (failure: Exception) {
                failure.message?.takeIf { it.isNotBlank() } ?: "Fasting action failed"
            }
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            val snapshot = runCatching { load(repos.first, repos.second, owner) }.getOrNull()
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            if (snapshot != null) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    schedule = snapshot.schedule,
                    active = snapshot.active,
                    history = snapshot.history,
                    message = message,
                )
            } else {
                mutableState.value = mutableState.value.copy(loading = false, message = message)
            }
        }
    }

    private companion object {
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
