package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.WorkoutSessionEntity
import com.nobelle.core.data.WorkoutSetEntity
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SetType
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.WatchWorkoutPayload
import com.nobelle.core.model.WorkoutTemplate
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Full payload of a completed workout: the session plus all of its sets. */
@Serializable
data class WorkoutPayload(val session: WorkoutSession, val sets: List<ExerciseSet>)

/**
 * Owner-scoped durable workout state. ACTIVE/PAUSED journeys stay local —
 * only finishing a session enqueues the immutable sync event for Drive.
 */
class WorkoutRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun start(
        owner: OwnerId,
        category: String,
        originDeviceId: String,
        startTimeZone: String,
        sourceOffsetMinutes: Int,
        templateId: String? = null,
    ): WorkoutSession = database.withTransaction {
        if (database.sessions().activeSession(owner.uid) != null) {
            throw IllegalStateException("An active or paused workout already exists for this owner")
        }
        val session = WorkoutSession(
            id = newId(),
            ownerUID = owner.uid,
            templateId = templateId,
            category = category,
            state = WorkoutState.ACTIVE,
            startedUtc = now(),
            startTimeZone = startTimeZone,
            sourceOffsetMinutes = sourceOffsetMinutes,
            originDeviceId = originDeviceId,
            leaseOwnerDeviceId = installationId,
            sequence = 1,
            revision = 1,
        )
        database.sessions().upsert(session.toEntity())
        session
    }

    /**
     * Starts an ACTIVE session prefilled from a template — a logging aid, not
     * auto-logging: reps, load and duration stay null for the user to enter
     * (§8C). Only the rest target carries over so the prefilled set rows keep
     * the intended recovery context. Same persistence path as [start]: the
     * journey stays local until [finish] enqueues the sync event.
     */
    suspend fun startFromTemplate(
        owner: OwnerId,
        template: WorkoutTemplate,
        originDeviceId: String,
        startTimeZone: String,
        sourceOffsetMinutes: Int,
    ): WorkoutSession = database.withTransaction {
        require(template.ownerUID == owner.uid) { "Template does not belong to the requested owner" }
        val session = start(
            owner, template.category, originDeviceId, startTimeZone,
            sourceOffsetMinutes, templateId = template.id,
        )
        var index = 0
        template.exercises.forEach { exercise ->
            repeat(exercise.sets) {
                val set = ExerciseSet(
                    id = newId(),
                    sessionId = session.id,
                    exerciseId = exercise.exerciseId,
                    setIndex = index++,
                    restSec = exercise.restSec,
                    notes = exercise.notes,
                    revision = 1,
                )
                database.workoutSets().upsert(set.toEntity(owner.uid))
            }
        }
        session
    }

    suspend fun active(owner: OwnerId): WorkoutSession? =
        database.sessions().activeSession(owner.uid)?.let { decodeSession(it, owner.uid) }

    suspend fun sets(owner: OwnerId, sessionId: String): List<ExerciseSet> =
        database.workoutSets().bySession(owner.uid, sessionId)
            .map { decodeSet(it, owner.uid, sessionId) }
            .sortedBy { it.setIndex }

    /**
     * Completed sessions with their sets, newest first — the history read.
     * A single corrupt payload row is skipped rather than blanking the list.
     */
    suspend fun completedWithSets(owner: OwnerId): List<WorkoutPayload> =
        database.sessions().list(owner.uid)
            .mapNotNull { runCatching { decodeSession(it, owner.uid) }.getOrNull() }
            .filter { it.state == WorkoutState.COMPLETED && it.deletedUtc == null }
            .map { session -> WorkoutPayload(session, sets(owner, session.id)) }

    /**
     * Watch-relayed workouts, newest first. Every stored column mirrors a
     * payload field, so display never decodes the wire JSON.
     */
    suspend fun watchWorkouts(owner: OwnerId): List<WatchWorkoutPayload> =
        database.watchWorkouts().list(owner.uid).map { row ->
            WatchWorkoutPayload(
                id = row.id,
                activity = row.activity,
                startedUtc = row.startedUtc,
                endedUtc = row.endedUtc,
                durationSec = row.durationSec,
                hrSampleCount = row.hrSampleCount,
                avgBpm = row.avgBpm,
                maxBpm = row.maxBpm,
            )
        }

    suspend fun pause(owner: OwnerId, sessionId: String): WorkoutSession =
        transition(owner, sessionId, setOf(WorkoutState.ACTIVE), WorkoutState.PAUSED)

    suspend fun resume(owner: OwnerId, sessionId: String): WorkoutSession =
        transition(owner, sessionId, setOf(WorkoutState.PAUSED), WorkoutState.ACTIVE)

    suspend fun addSet(
        owner: OwnerId,
        sessionId: String,
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
    ): ExerciseSet = database.withTransaction {
        val session = loadSession(owner, sessionId)
        require(session.state == WorkoutState.ACTIVE || session.state == WorkoutState.PAUSED) {
            "Sets can only be added to an active or paused workout"
        }
        require(reps == null || reps >= 0) { "reps must be nonnegative" }
        require(loadKg == null || loadKg >= 0) { "loadKg must be nonnegative" }
        require(durationSec == null || durationSec >= 0) { "durationSec must be nonnegative" }
        require(restSec == null || restSec >= 0) { "restSec must be nonnegative" }
        require(rpe == null || rpe in 0.0..10.0) { "rpe must be within 0..10" }
        require(rir == null || rir >= 0) { "rir must be nonnegative" }
        val existing = database.workoutSets().bySession(owner.uid, sessionId)
            .map { decodeSet(it, owner.uid, sessionId) }
        val set = ExerciseSet(
            id = newId(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            setIndex = (existing.maxOfOrNull { it.setIndex } ?: -1) + 1,
            reps = reps,
            loadKg = loadKg,
            rpe = rpe,
            rir = rir,
            durationSec = durationSec,
            restSec = restSec,
            tempo = tempo,
            setType = setType,
            groupId = groupId,
            substitutedForExerciseId = substitutedForExerciseId,
            completedUtc = now(),
            painFlag = painFlag,
            notes = notes,
            revision = 1,
        )
        database.workoutSets().upsert(set.toEntity(owner.uid))
        set
    }

    /**
     * Fills a prefilled slot in place (UPDATE, never an appended row). Only
     * user-entered values are written — the repository never invents reps or
     * load (§8C). A slot is fillable exactly once: a completedUtc value marks
     * it logged and the guided UI treats it as read-only.
     */
    suspend fun completeSetSlot(
        owner: OwnerId,
        sessionId: String,
        setId: String,
        reps: Int?,
        loadKg: Double?,
        durationSec: Int?,
        rpe: Double?,
        notes: String?,
    ): ExerciseSet = database.withTransaction {
        val session = loadSession(owner, sessionId)
        require(session.state == WorkoutState.ACTIVE || session.state == WorkoutState.PAUSED) {
            "Sets can only be logged on an active or paused workout"
        }
        require(reps == null || reps >= 0) { "reps must be nonnegative" }
        require(loadKg == null || loadKg >= 0) { "loadKg must be nonnegative" }
        require(durationSec == null || durationSec >= 0) { "durationSec must be nonnegative" }
        require(rpe == null || rpe in 0.0..10.0) { "rpe must be within 0..10" }
        val slot = loadSet(owner, sessionId, setId)
        require(slot.completedUtc == null) { "This set slot is already logged" }
        val completed = slot.copy(
            reps = reps,
            loadKg = loadKg,
            durationSec = durationSec,
            rpe = rpe,
            notes = notes?.takeIf { it.isNotBlank() } ?: slot.notes,
            completedUtc = now(),
            revision = slot.revision + 1,
        )
        database.workoutSets().upsert(completed.toEntity(owner.uid))
        completed
    }

    /**
     * Swaps an open slot to a different exercise the user chose (e.g. an
     * easier or harder variant). The original exercise id is preserved on
     * [ExerciseSet.substitutedForExerciseId] so the change keeps honest
     * provenance instead of looking like it was always planned.
     */
    suspend fun substituteSetSlot(
        owner: OwnerId,
        sessionId: String,
        setId: String,
        exerciseId: String,
    ): ExerciseSet = database.withTransaction {
        val session = loadSession(owner, sessionId)
        require(session.state == WorkoutState.ACTIVE || session.state == WorkoutState.PAUSED) {
            "Sets can only be changed on an active or paused workout"
        }
        require(exerciseId.isNotBlank()) { "substitute exercise id must not be blank" }
        val slot = loadSet(owner, sessionId, setId)
        require(slot.completedUtc == null) { "A logged set cannot be substituted" }
        if (slot.exerciseId == exerciseId) return@withTransaction slot
        val swapped = slot.copy(
            exerciseId = exerciseId,
            substitutedForExerciseId = slot.substitutedForExerciseId ?: slot.exerciseId,
            revision = slot.revision + 1,
        )
        database.workoutSets().upsert(swapped.toEntity(owner.uid))
        swapped
    }

    suspend fun finish(owner: OwnerId, sessionId: String): WorkoutSession = database.withTransaction {
        val session = loadSession(owner, sessionId)
        require(session.state == WorkoutState.ACTIVE || session.state == WorkoutState.PAUSED) {
            "Only an active or paused workout can be finished (was ${session.state})"
        }
        val finished = advance(session, WorkoutState.COMPLETED, ended = true)
        database.sessions().upsert(finished.toEntity())
        val sets = database.workoutSets().bySession(owner.uid, sessionId)
            .map { decodeSet(it, owner.uid, sessionId) }
            .sortedBy { it.setIndex }
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = finished.id,
            entityType = ENTITY_TYPE,
            operationUuid = newId(),
            revision = finished.revision,
            op = SyncEvent.Op.UPSERT,
            payloadJson = durableJson.encodeToString(WorkoutPayload(finished, sets)),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
        finished
    }

    /** Discard is a local-only transition: an unsynced journey never reaches Drive. */
    suspend fun discard(owner: OwnerId, sessionId: String): WorkoutSession =
        transition(owner, sessionId, setOf(WorkoutState.ACTIVE, WorkoutState.PAUSED), WorkoutState.DISCARDED)

    private suspend fun transition(
        owner: OwnerId,
        sessionId: String,
        allowedFrom: Set<WorkoutState>,
        target: WorkoutState,
    ): WorkoutSession = database.withTransaction {
        val session = loadSession(owner, sessionId)
        require(session.state in allowedFrom) {
            "Cannot move workout from ${session.state} to $target"
        }
        val updated = advance(session, target, ended = target == WorkoutState.DISCARDED)
        database.sessions().upsert(updated.toEntity())
        updated
    }

    private fun advance(session: WorkoutSession, target: WorkoutState, ended: Boolean): WorkoutSession =
        session.copy(
            state = target,
            endedUtc = if (ended) now() else session.endedUtc,
            sequence = session.sequence + 1,
            revision = session.revision + 1,
        )

    private suspend fun loadSet(owner: OwnerId, sessionId: String, setId: String): ExerciseSet {
        val entity = database.workoutSets().byId(owner.uid, setId)
            ?: throw NoSuchElementException("Exercise set not found for this owner")
        return decodeSet(entity, owner.uid, sessionId)
    }

    private suspend fun loadSession(owner: OwnerId, sessionId: String): WorkoutSession {
        val entity = database.sessions().byId(owner.uid, sessionId)
            ?: throw NoSuchElementException("Workout session not found for this owner")
        return decodeSession(entity, owner.uid)
    }

    private fun decodeSession(entity: WorkoutSessionEntity, ownerUID: String): WorkoutSession {
        check(entity.ownerUID == ownerUID) { "Session row owner does not match the requested owner" }
        val session = try {
            durableJson.decodeFromString(WorkoutSession.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt workout session payload", failure)
        }
        check(session.id == entity.id && session.ownerUID == ownerUID) {
            "Session payload does not match its row identity"
        }
        return session
    }

    private fun decodeSet(entity: WorkoutSetEntity, ownerUID: String, sessionId: String): ExerciseSet {
        check(entity.ownerUID == ownerUID) { "Set row owner does not match the requested owner" }
        val set = try {
            durableJson.decodeFromString(ExerciseSet.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt exercise set payload", failure)
        }
        check(set.id == entity.id && set.sessionId == sessionId) {
            "Set payload does not match its row identity"
        }
        return set
    }

    private fun WorkoutSession.toEntity() = WorkoutSessionEntity(
        id = id,
        ownerUID = ownerUID,
        state = state.name,
        startedUtc = startedUtc?.toEpochMilliseconds(),
        endedUtc = endedUtc?.toEpochMilliseconds(),
        sequence = sequence,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
    )

    private fun ExerciseSet.toEntity(ownerUID: String) = WorkoutSetEntity(
        ownerUID = ownerUID,
        id = id,
        sessionId = sessionId,
        completedUtc = completedUtc?.toEpochMilliseconds(),
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
    )

    companion object {
        const val ENTITY_TYPE = "workout_session"
    }
}
