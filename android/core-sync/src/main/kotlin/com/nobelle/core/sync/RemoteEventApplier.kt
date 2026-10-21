package com.nobelle.core.sync

import androidx.room.withTransaction
import com.nobelle.core.data.FastingScheduleDao
import com.nobelle.core.data.FastingScheduleEntity
import com.nobelle.core.data.FastingSessionDao
import com.nobelle.core.data.FastingSessionEntity
import com.nobelle.core.data.GymVisitDao
import com.nobelle.core.data.GymVisitEntity
import com.nobelle.core.data.HealthObservationDao
import com.nobelle.core.data.HealthObservationEntity
import com.nobelle.core.data.WorkoutTemplateDao
import com.nobelle.core.data.WorkoutTemplateEntity
import com.nobelle.core.data.repository.WorkoutTemplateRepository
import com.nobelle.core.model.WorkoutTemplate
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.OutboxDao
import com.nobelle.core.data.OutdoorSessionDao
import com.nobelle.core.data.OutdoorSessionEntity
import com.nobelle.core.data.SessionDao
import com.nobelle.core.data.TombstoneDao
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.data.WatchWorkoutDao
import com.nobelle.core.data.WatchWorkoutEntity
import com.nobelle.core.data.WorkoutSetDao
import com.nobelle.core.data.WorkoutSetEntity
import com.nobelle.core.data.WorkoutSessionEntity
import com.nobelle.core.data.repository.FastingScheduleRepository
import com.nobelle.core.data.repository.FastingSessionRepository
import com.nobelle.core.data.repository.GymVisitRepository
import com.nobelle.core.data.repository.HealthObservationRepository
import com.nobelle.core.data.repository.OutdoorSessionRepository
import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.data.repository.WorkoutRepository
import com.nobelle.core.data.repository.toOutboxEntity
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.FastingSchedule
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.OutdoorSession
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.SyncPaths
import com.nobelle.core.model.WatchSyncCodec
import com.nobelle.core.model.WorkoutSession
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * The Room surface the applier needs, kept narrow so unit tests can fake it
 * without a database. Production wiring wraps [NobelleDatabase]; every DAO
 * call stays owner-scoped.
 */
interface RemoteApplyStore {
    fun outbox(): OutboxDao
    fun sessions(): SessionDao
    fun workoutSets(): WorkoutSetDao
    fun gymVisits(): GymVisitDao
    fun fastingSessions(): FastingSessionDao
    fun fastingSchedules(): FastingScheduleDao
    fun healthObservations(): HealthObservationDao
    fun watchWorkouts(): WatchWorkoutDao
    fun outdoorSessions(): OutdoorSessionDao
    fun workoutTemplates(): WorkoutTemplateDao
    fun tombstones(): TombstoneDao
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * Applies downloaded SyncEvents to the durable owner-scoped store (§13).
 *
 * Dedupe marker: an outbox row in state APPLIED. Own events still PENDING or
 * already UPLOADED are therefore applied once — their writes are idempotent
 * (REPLACE on the same keys) — which is what restores state after a reinstall
 * and lands watch-relayed workouts. A remote op is only marked APPLIED after
 * its domain effect committed, so a crash mid-apply replays the op rather
 * than losing it. Unknown entity types and malformed payloads are left
 * unmarked: a later version may know how to apply them, and a corrupt file
 * should not become permanent dedupe.
 */
class RemoteEventApplier(
    private val store: RemoteApplyStore,
    private val ownerUid: String,
    private val now: () -> Instant = { Clock.System.now() },
) : SyncEngine.RemoteApplier {

    constructor(
        database: NobelleDatabase,
        ownerUid: String,
    ) : this(RoomRemoteApplyStore(database), ownerUid)

    override suspend fun seenOperation(operationUuid: String): Boolean =
        store.outbox().appliedExists(ownerUid, operationUuid)

    override suspend fun apply(event: SyncEvent) {
        if (event.accountNamespace != ownerUid) return
        try {
            store.inTransaction {
                if (applyDomainEvent(event)) {
                    // Enqueue covers ops this outbox never produced (remote
                    // origin); markApplied flips pre-existing PENDING/UPLOADED
                    // rows — e.g. own events on a reinstall or relayed watch
                    // events — without touching attempts or lastError.
                    store.outbox().enqueue(
                        event.toOutboxEntity(now().toEpochMilliseconds())
                            .copy(state = "APPLIED")
                    )
                    store.outbox().markApplied(ownerUid, event.operationUuid)
                }
            }
        } catch (failure: Exception) {
            // Malformed payload or identity mismatch: the transaction rolled
            // back and the op stays unapplied for diagnosis.
        }
    }

    /** Returns false when the op must not be recorded as applied. */
    private suspend fun applyDomainEvent(event: SyncEvent): Boolean = when (event.op) {
        SyncEvent.Op.UPSERT -> applyUpsert(event)
        SyncEvent.Op.DELETE -> applyDelete(event)
    }

    private suspend fun applyUpsert(event: SyncEvent): Boolean {
        // A tombstone wins over any later-arriving UPSERT — deleted data must
        // not reappear (§13) — but the op itself counts as processed.
        if (store.tombstones().byEntity(ownerUid, event.entityType, event.entityId) != null) {
            return true
        }
        when (event.entityType) {
            WorkoutRepository.ENTITY_TYPE -> {
                val payload = json.decodeFromString(WorkoutPayload.serializer(), event.payloadJson)
                val session = payload.session
                if (session.id != event.entityId || session.ownerUID != ownerUid ||
                    payload.sets.any { it.sessionId != session.id }
                ) {
                    return false
                }
                store.sessions().upsert(
                    WorkoutSessionEntity(
                        ownerUID = ownerUid,
                        id = session.id,
                        state = session.state.name,
                        startedUtc = session.startedUtc?.toEpochMilliseconds(),
                        endedUtc = session.endedUtc?.toEpochMilliseconds(),
                        sequence = session.sequence,
                        payloadJson = json.encodeToString(WorkoutSession.serializer(), session),
                        revision = session.revision,
                    )
                )
                payload.sets.forEach { set ->
                    store.workoutSets().upsert(
                        WorkoutSetEntity(
                            ownerUID = ownerUid,
                            id = set.id,
                            sessionId = set.sessionId,
                            completedUtc = set.completedUtc?.toEpochMilliseconds(),
                            payloadJson = json.encodeToString(ExerciseSet.serializer(), set),
                            revision = set.revision,
                        )
                    )
                }
            }
            GymVisitRepository.ENTITY_TYPE -> {
                val visit = json.decodeFromString(GymVisit.serializer(), event.payloadJson)
                if (visit.id != event.entityId || visit.ownerUID != ownerUid) return false
                store.gymVisits().upsert(
                    GymVisitEntity(
                        ownerUID = ownerUid,
                        id = visit.id,
                        startInstant = visit.startInstant.toEpochMilliseconds(),
                        endInstant = visit.endInstant?.toEpochMilliseconds(),
                        localAttendanceDate = visit.localAttendanceDate,
                        confirmed = visit.confirmed,
                        payloadJson = json.encodeToString(GymVisit.serializer(), visit),
                        revision = visit.revision,
                        deletedUtc = visit.deletedUtc?.toEpochMilliseconds(),
                    )
                )
            }
            FastingSessionRepository.ENTITY_TYPE -> {
                val session = json.decodeFromString(FastingSession.serializer(), event.payloadJson)
                if (session.id != event.entityId || session.ownerUID != ownerUid) return false
                store.fastingSessions().upsert(
                    FastingSessionEntity(
                        ownerUID = ownerUid,
                        id = session.id,
                        actualStartInstant = session.actualStartInstant.toEpochMilliseconds(),
                        actualEndInstant = session.actualEndInstant?.toEpochMilliseconds(),
                        status = session.status.name,
                        payloadJson = json.encodeToString(FastingSession.serializer(), session),
                        revision = session.revision,
                        deletedUtc = session.deletedUtc?.toEpochMilliseconds(),
                    )
                )
            }
            FastingScheduleRepository.ENTITY_TYPE -> {
                val schedule = json.decodeFromString(FastingSchedule.serializer(), event.payloadJson)
                if (schedule.id != event.entityId || schedule.ownerUID != ownerUid) return false
                store.fastingSchedules().upsert(
                    FastingScheduleEntity(
                        ownerUID = ownerUid,
                        id = schedule.id,
                        enabled = schedule.enabled,
                        payloadJson = json.encodeToString(FastingSchedule.serializer(), schedule),
                        revision = schedule.revision,
                    )
                )
            }
            HealthObservationRepository.ENTITY_TYPE -> {
                val observation =
                    json.decodeFromString(HealthObservation.serializer(), event.payloadJson)
                if (observation.id != event.entityId || observation.ownerUID != ownerUid) {
                    return false
                }
                store.healthObservations().upsert(
                    HealthObservationEntity(
                        ownerUID = ownerUid,
                        id = observation.id,
                        marker = observation.marker,
                        measuredUtc = observation.measuredUtc.toEpochMilliseconds(),
                        sourceNamespace = observation.source?.sourceNamespace,
                        sourceRecordId = observation.source?.sourceRecordId,
                        payloadJson = json.encodeToString(
                            HealthObservation.serializer(), observation),
                        revision = observation.revision,
                        deletedUtc = observation.deletedUtc?.toEpochMilliseconds(),
                    )
                )
            }
            WorkoutTemplateRepository.ENTITY_TYPE -> {
                val template = json.decodeFromString(WorkoutTemplate.serializer(), event.payloadJson)
                if (template.id != event.entityId || template.ownerUID != ownerUid ||
                    template.revision != event.revision) return false
                val existing = store.workoutTemplates().byId(ownerUid, template.id)
                if (existing != null && existing.revision > template.revision) return true
                store.workoutTemplates().upsert(WorkoutTemplateEntity(
                    ownerUID = ownerUid, id = template.id, name = template.name, category = template.category,
                    payloadJson = json.encodeToString(WorkoutTemplate.serializer(), template), revision = template.revision,
                ))
            }
            OutdoorSessionRepository.ENTITY_TYPE -> {
                val session = json.decodeFromString(OutdoorSession.serializer(), event.payloadJson)
                if (session.id != event.entityId || session.ownerUID != ownerUid) return false
                // Last-writer-wins by revision: a staler event arriving after
                // a newer row must not regress the session, but still counts
                // as applied.
                val existing = store.outdoorSessions().byId(ownerUid, session.id)
                if (existing != null && existing.revision > session.revision) return true
                store.outdoorSessions().upsert(
                    OutdoorSessionEntity(
                        ownerUID = ownerUid,
                        id = session.id,
                        activity = session.activity,
                        startedUtc = session.startedUtc.toEpochMilliseconds(),
                        endedUtc = session.endedUtc.toEpochMilliseconds(),
                        durationSec = session.durationSec,
                        distanceMeters = session.distanceMeters,
                        pointCount = session.pointCount,
                        payloadJson = json.encodeToString(OutdoorSession.serializer(), session),
                        revision = session.revision,
                        createdUtc = session.createdUtc.toEpochMilliseconds(),
                        updatedUtc = session.updatedUtc?.toEpochMilliseconds(),
                        deletedUtc = session.deletedUtc?.toEpochMilliseconds(),
                    )
                )
            }
            SyncPaths.ENTITY_TYPE_WATCH_WORKOUT -> {
                val payload = WatchSyncCodec.decodeWorkoutPayload(event.payloadJson)
                if (payload.id != event.entityId) return false
                store.watchWorkouts().upsert(
                    WatchWorkoutEntity(
                        ownerUID = ownerUid,
                        id = payload.id,
                        activity = payload.activity,
                        startedUtc = payload.startedUtc,
                        endedUtc = payload.endedUtc,
                        durationSec = payload.durationSec,
                        hrSampleCount = payload.hrSampleCount,
                        avgBpm = payload.avgBpm,
                        maxBpm = payload.maxBpm,
                        payloadJson = WatchSyncCodec.encodeWorkoutPayload(payload),
                        revision = event.revision,
                    )
                )
            }
            // Forward compatibility: an entity type this build does not know
            // stays unapplied so a later version can still process it.
            else -> return false
        }
        return true
    }

    private suspend fun applyDelete(event: SyncEvent): Boolean {
        val known = when (event.entityType) {
            WorkoutRepository.ENTITY_TYPE,
            GymVisitRepository.ENTITY_TYPE,
            FastingSessionRepository.ENTITY_TYPE,
            FastingScheduleRepository.ENTITY_TYPE,
            HealthObservationRepository.ENTITY_TYPE,
            OutdoorSessionRepository.ENTITY_TYPE,
            WorkoutTemplateRepository.ENTITY_TYPE,
            SyncPaths.ENTITY_TYPE_WATCH_WORKOUT -> true
            else -> false
        }
        if (!known) return false
        store.tombstones().upsert(
            TombstoneEntity(
                ownerUID = ownerUid,
                entityType = event.entityType,
                entityId = event.entityId,
                deletedUtc = event.utc.toEpochMilliseconds(),
                operationUuid = event.operationUuid,
            )
        )
        when (event.entityType) {
            WorkoutRepository.ENTITY_TYPE -> {
                store.workoutSets().deleteBySession(ownerUid, event.entityId)
                store.sessions().hardDelete(ownerUid, event.entityId)
            }
            GymVisitRepository.ENTITY_TYPE ->
                store.gymVisits().hardDelete(ownerUid, event.entityId)
            FastingSessionRepository.ENTITY_TYPE ->
                store.fastingSessions().hardDelete(ownerUid, event.entityId)
            FastingScheduleRepository.ENTITY_TYPE ->
                store.fastingSchedules().hardDelete(ownerUid, event.entityId)
            HealthObservationRepository.ENTITY_TYPE ->
                store.healthObservations().hardDelete(ownerUid, event.entityId)
            OutdoorSessionRepository.ENTITY_TYPE ->
                store.outdoorSessions().hardDelete(ownerUid, event.entityId)
            WorkoutTemplateRepository.ENTITY_TYPE ->
                store.workoutTemplates().hardDelete(ownerUid, event.entityId)
            SyncPaths.ENTITY_TYPE_WATCH_WORKOUT ->
                store.watchWorkouts().hardDelete(ownerUid, event.entityId)
        }
        return true
    }

    private companion object {
        // Same canonical shape as the repositories' codec (unknown keys are
        // tolerated so newer payloads still apply).
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

private class RoomRemoteApplyStore(private val database: NobelleDatabase) : RemoteApplyStore {
    override fun outbox() = database.outbox()
    override fun sessions() = database.sessions()
    override fun workoutSets() = database.workoutSets()
    override fun gymVisits() = database.gymVisits()
    override fun fastingSessions() = database.fastingSessions()
    override fun fastingSchedules() = database.fastingSchedules()
    override fun healthObservations() = database.healthObservations()
    override fun watchWorkouts() = database.watchWorkouts()
    override fun outdoorSessions() = database.outdoorSessions()
    override fun workoutTemplates() = database.workoutTemplates()
    override fun tombstones() = database.tombstones()
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}
