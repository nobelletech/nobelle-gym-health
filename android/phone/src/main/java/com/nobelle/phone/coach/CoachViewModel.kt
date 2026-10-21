package com.nobelle.phone.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.coaching.ExerciseCatalog
import com.nobelle.core.data.repository.WorkoutRepository
import com.nobelle.core.data.repository.WorkoutTemplateRepository
import com.nobelle.core.model.ExerciseDefinition
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.WorkoutTemplate
import com.nobelle.phone.NobelleApplication
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn

/**
 * Coach state: the bundled, reviewed exercise catalog plus the owner's own
 * workout templates. Templates persist through WorkoutTemplateRepository
 * (row + sync outbox in one transaction); starting one reuses
 * WorkoutRepository.startFromTemplate, which prefills set slots — it never
 * logs values for the user.
 */
class CoachViewModel(application: Application) : AndroidViewModel(application) {
    data class CoachUiState(
        val ownerAvailable: Boolean = false,
        val catalog: List<ExerciseDefinition> = emptyList(),
        val catalogById: Map<String, ExerciseDefinition> = emptyMap(),
        val templates: List<WorkoutTemplate> = emptyList(),
        val loading: Boolean = false,
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val templates: WorkoutTemplateRepository? = try {
        WorkoutTemplateRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }
    private val workouts: WorkoutRepository? = try {
        WorkoutRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null
    }

    private val mutableState = MutableStateFlow(
        runCatching {
            CoachUiState(
                catalog = ExerciseCatalog.all(),
                catalogById = ExerciseCatalog.byId(),
                message = if (templates == null || workouts == null) INIT_ERROR else null,
            )
        }.getOrElse {
            CoachUiState(message = "The bundled exercise catalog failed to load")
        }
    )
    val state: StateFlow<CoachUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L

    fun selectOwner(owner: OwnerId?) {
        val generation = ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        if (owner == null) {
            mutableState.value = mutableState.value.copy(
                ownerAvailable = false,
                templates = emptyList(),
                message = if (templates == null || workouts == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            mutableState.value = mutableState.value.copy(
                ownerAvailable = templates != null && workouts != null,
                templates = emptyList(),
                message = if (templates == null || workouts == null) INIT_ERROR else null,
            )
        }
        val repo = templates ?: run {
            mutableState.value = mutableState.value.copy(ownerAvailable = false, message = INIT_ERROR)
            return
        }
        viewModelScope.launch {
            val list = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(ownerAvailable = true, templates = list)
        }
    }

    /** Creates (id == null) or edits a user template and queues the sync event. */
    fun saveTemplate(id: String?, name: String, category: String, exercises: List<com.nobelle.core.model.TemplateExercise>) =
        mutate { owner ->
            val repo = templates ?: return@mutate INIT_ERROR
            repo.upsert(
                owner,
                WorkoutTemplate(
                    id = id ?: UUID.randomUUID().toString(),
                    ownerUID = owner.uid,
                    name = name.trim(),
                    category = category.trim(),
                    exercises = exercises,
                ),
            )
            if (id == null) "Template saved" else "Template updated"
        }

    fun deleteTemplate(id: String) = mutate { owner ->
        val repo = templates ?: return@mutate INIT_ERROR
        repo.delete(owner, id)
        "Template deleted"
    }

    /**
     * Prefills an ACTIVE session from the template — reps, load and duration
     * are left blank for the user to log on the Today tab (§8C).
     */
    fun startTemplate(id: String) = mutate { owner ->
        val repo = templates ?: return@mutate INIT_ERROR
        val workoutRepo = workouts ?: return@mutate INIT_ERROR
        val template = repo.get(owner, id) ?: return@mutate "Template not found"
        val instant = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        workoutRepo.startFromTemplate(
            owner = owner,
            template = template,
            originDeviceId = app.installationId,
            startTimeZone = zone.id,
            sourceOffsetMinutes = instant.offsetIn(zone).totalSeconds / 60,
        )
        "Workout started from \"${template.name}\" — sets are blank slots to fill on the Today tab."
    }

    private fun mutate(block: suspend (OwnerId) -> String) {
        val owner = selectedOwner ?: return
        if (mutableState.value.loading) return
        val generation = selectionGeneration
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val message = try {
                block(owner)
            } catch (failure: Exception) {
                failure.message?.takeIf { it.isNotBlank() } ?: "Coach action failed"
            }
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            val list = runCatching { templates?.list(owner) }.getOrNull() ?: emptyList()
            mutableState.value = mutableState.value.copy(
                loading = false,
                templates = list,
                message = message,
            )
        }
    }

    private companion object {
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
