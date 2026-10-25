package com.nobelle.phone.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.coaching.ExerciseCatalog
import com.nobelle.core.coaching.PlanStatus
import com.nobelle.core.coaching.PlanningEngine
import com.nobelle.core.data.repository.TrainingPlanRepository
import com.nobelle.core.data.repository.WorkoutRepository
import com.nobelle.core.data.repository.WorkoutTemplateRepository
import com.nobelle.core.model.ExerciseDefinition
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TrainingPlan
import com.nobelle.core.model.UserProfile
import com.nobelle.core.model.WorkoutTemplate
import com.nobelle.phone.NobelleApplication
import java.time.DayOfWeek
import java.time.LocalDate
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
        val currentPlan: TrainingPlan? = null,
        /** Engine preview awaiting the user's Accept — never auto-saved. */
        val suggestedPlan: TrainingPlan? = null,
        /** True when no persisted profile exists and defaults drove the suggestion. */
        val profileIsDefault: Boolean = false,
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
    private val plans: TrainingPlanRepository? = try {
        TrainingPlanRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null
    }
    private val engine = PlanningEngine()

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
                currentPlan = null,
                suggestedPlan = null,
                message = if (templates == null || workouts == null) INIT_ERROR else null,
            )
            return
        }
        if (owner.uid != previous?.uid) {
            mutableState.value = mutableState.value.copy(
                ownerAvailable = templates != null && workouts != null,
                templates = emptyList(),
                currentPlan = null,
                suggestedPlan = null,
                message = if (templates == null || workouts == null) INIT_ERROR else null,
            )
        }
        val repo = templates ?: run {
            mutableState.value = mutableState.value.copy(ownerAvailable = false, message = INIT_ERROR)
            return
        }
        viewModelScope.launch {
            val list = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            val plan = loadPlan(owner)
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            mutableState.value = mutableState.value.copy(
                ownerAvailable = true, templates = list, currentPlan = plan,
            )
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

    /**
     * Runs the deterministic PlanningEngine for this week (Monday start) and
     * stages the result as a reviewable suggestion — nothing is saved until
     * the user accepts it. No UserProfile is persisted yet (onboarding does
     * not store one), so a documented default profile drives the engine:
     * empty preferredDays/goals → the engine's own defaults.
     */
    fun generateWeek() {
        val owner = selectedOwner ?: return
        val profile = UserProfile(ownerUID = owner.uid, createdUtc = Clock.System.now())
        val monday = LocalDate.now().with(DayOfWeek.MONDAY).toString()
        val suggestion = engine.suggestWeek(profile, monday)
        mutableState.value = mutableState.value.copy(
            suggestedPlan = suggestion,
            profileIsDefault = true,
            message = "No saved profile yet — this suggestion uses default days and " +
                "categories. It is a suggestion you can change, not a prescription.",
        )
    }

    fun dismissSuggestion() {
        mutableState.value = mutableState.value.copy(suggestedPlan = null)
    }

    /** Saves the reviewed suggestion through the repository (row + outbox). */
    fun acceptSuggestion() = mutate { owner ->
        val repo = plans ?: return@mutate INIT_ERROR
        val suggestion = mutableState.value.suggestedPlan ?: return@mutate "No suggestion to accept"
        repo.upsert(owner, suggestion)
        mutableState.value = mutableState.value.copy(suggestedPlan = null)
        "Week plan saved — edit it any time; it syncs as one plan."
    }

    /** Skip / unskip (PLANNED) toggles; completion itself is derived. */
    fun setPlannedStatus(planId: String, sessionId: String, status: PlannedSession.Status) =
        mutate { owner ->
            val repo = plans ?: return@mutate INIT_ERROR
            repo.setPlannedSessionStatus(owner, planId, sessionId, status)
            when (status) {
                PlannedSession.Status.SKIPPED -> "Session marked as skipped"
                PlannedSession.Status.PLANNED -> "Session back on the plan"
                PlannedSession.Status.COMPLETED -> "Session marked completed"
            }
        }

    /** Adds or replaces a planned session (day, category, optional HH:mm time). */
    fun upsertPlannedSession(
        planId: String,
        sessionId: String,
        localDate: String,
        category: String,
        startTimeLocal: String?,
        templateId: String?,
    ) = mutate { owner ->
        val repo = plans ?: return@mutate INIT_ERROR
        val plan = repo.get(owner, planId) ?: return@mutate "Plan not found"
        val existing = plan.plannedSessions.firstOrNull { it.id == sessionId }
        val session = (existing ?: PlannedSession(
            id = sessionId,
            ownerUID = plan.ownerUID,
            localDate = localDate,
            templateId = null,
            category = category,
        )).copy(
            category = category,
            startTimeLocal = startTimeLocal?.ifBlank { null },
            templateId = templateId,
            revision = (existing?.revision ?: 0) + 1,
        )
        val sessions = plan.plannedSessions
            .filter { it.id != sessionId } + session
        repo.upsert(owner, plan.copy(plannedSessions = sessions, revision = plan.revision + 1))
        if (existing == null) "Session added to the plan" else "Session updated"
    }

    /** Removes a planned session from the week. */
    fun removePlannedSession(planId: String, sessionId: String) = mutate { owner ->
        val repo = plans ?: return@mutate INIT_ERROR
        val plan = repo.get(owner, planId) ?: return@mutate "Plan not found"
        if (plan.plannedSessions.none { it.id == sessionId }) return@mutate "Session not found"
        val sessions = plan.plannedSessions.filter { it.id != sessionId }
        repo.upsert(owner, plan.copy(plannedSessions = sessions, revision = plan.revision + 1))
        "Session removed from the plan"
    }

    /**
     * Starts the planned session: from its template when it has one, else a
     * blank session in the planned category. Starting counts as completing
     * the plan row (PlanStatus also derives completion when a matching
     * completed workout lands on the same date + category).
     */
    fun startPlannedSession(planId: String, sessionId: String) = mutate { owner ->
        val plansRepo = plans ?: return@mutate INIT_ERROR
        val workoutRepo = workouts ?: return@mutate INIT_ERROR
        val plan = plansRepo.get(owner, planId) ?: return@mutate "Plan not found"
        val session = plan.plannedSessions.firstOrNull { it.id == sessionId }
            ?: return@mutate "Planned session not found"
        val instant = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val template = session.templateId?.let { templates?.get(owner, it) }
        if (session.templateId != null && template == null) {
            return@mutate "The template for this session is no longer available"
        }
        if (template != null) {
            workoutRepo.startFromTemplate(
                owner = owner,
                template = template,
                originDeviceId = app.installationId,
                startTimeZone = zone.id,
                sourceOffsetMinutes = instant.offsetIn(zone).totalSeconds / 60,
            )
        } else {
            workoutRepo.start(
                owner = owner,
                category = session.category,
                originDeviceId = app.installationId,
                startTimeZone = zone.id,
                sourceOffsetMinutes = instant.offsetIn(zone).totalSeconds / 60,
            )
        }
        plansRepo.setPlannedSessionStatus(owner, planId, sessionId, PlannedSession.Status.COMPLETED)
        "Workout started for ${session.category.replace('_', ' ')} — fill in sets on the Today tab."
    }

    /**
     * Reads the owner's plan for the current week (falling back to the latest
     * stored plan) and folds in derived completions: when a completed workout
     * matches a planned row's date+category or template, the stored plan is
     * updated once through the repository so the status syncs.
     */
    private suspend fun loadPlan(owner: OwnerId): TrainingPlan? {
        val repo = plans ?: return null
        val plan = runCatching {
            repo.currentWeek(owner).firstOrNull() ?: repo.list(owner).firstOrNull()
        }.getOrNull() ?: return null
        val completed = runCatching {
            workouts?.completedWithSets(owner)?.map { it.session }
        }.getOrNull() ?: return plan
        val derived = PlanStatus.withDerivedCompletions(plan, completed ?: emptyList())
        if (derived == plan) return plan
        return runCatching { repo.upsert(owner, derived) }.getOrDefault(plan)
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
            val plan = loadPlan(owner)
            runCatching { PlanReminders.sync(app, plan) }
            mutableState.value = mutableState.value.copy(
                loading = false,
                templates = list,
                currentPlan = plan ?: mutableState.value.currentPlan,
                message = message,
            )
        }
    }

    private companion object {
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
