package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.GymVisitEntity
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.encodeToString

/** Owner-scoped durable gym attendance. Deletes are soft marks paired with a
 *  tombstone and a DELETE sync event sharing one operation UUID. */
class GymVisitRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun get(owner: OwnerId, id: String): GymVisit? =
        database.gymVisits().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<GymVisit> =
        database.gymVisits().list(owner.uid).map { decode(it, owner.uid) }

    /** Persists the visit as given — attendance date and instants are never recomputed. */
    suspend fun upsert(owner: OwnerId, visit: GymVisit): GymVisit = database.withTransaction {
        require(visit.ownerUID == owner.uid) { "Gym visit does not belong to the requested owner" }
        persist(owner, visit)
    }

    /**
     * Logs a new confirmed visit. The attendance day is always the start
     * instant rendered in the timezone the user was in at creation.
     */
    suspend fun create(
        owner: OwnerId,
        start: Instant,
        originalTimeZone: String,
        gymLabel: String? = null,
        category: String? = null,
        notes: String? = null,
    ): GymVisit = upsert(
        owner,
        GymVisit(
            id = newId(),
            ownerUID = owner.uid,
            startInstant = start,
            endInstant = null,
            originalTimeZone = originalTimeZone,
            localAttendanceDate = GymVisit.attendanceDate(start, TimeZone.of(originalTimeZone)),
            gymLabel = gymLabel?.trim()?.ifBlank { null },
            category = category?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            confirmed = true,
            revision = 1,
        )
    )

    suspend fun end(owner: OwnerId, id: String, end: Instant): GymVisit = database.withTransaction {
        val visit = decodeRequired(owner, id)
        require(visit.deletedUtc == null) { "Gym visit is already deleted" }
        require(end >= visit.startInstant) { "A visit cannot end before it starts" }
        persist(owner, visit.copy(endInstant = end, revision = visit.revision + 1))
    }

    suspend fun edit(
        owner: OwnerId,
        id: String,
        start: Instant,
        originalTimeZone: String,
        gymLabel: String?,
        category: String?,
        notes: String?,
    ): GymVisit = database.withTransaction {
        val visit = decodeRequired(owner, id)
        require(visit.deletedUtc == null) { "Gym visit is already deleted" }
        persist(
            owner,
            visit.copy(
                startInstant = start,
                originalTimeZone = originalTimeZone,
                localAttendanceDate = GymVisit.attendanceDate(start, TimeZone.of(originalTimeZone)),
                endInstant = visit.endInstant?.takeIf { it >= start },
                gymLabel = gymLabel?.trim()?.ifBlank { null },
                category = category?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                revision = visit.revision + 1,
            )
        )
    }

    /** Idempotently attaches a workout record; an existing link is a no-op. */
    suspend fun linkWorkout(owner: OwnerId, visitId: String, workoutId: String): GymVisit =
        database.withTransaction {
            val workout = workoutId.trim()
            require(workout.isNotEmpty()) { "Workout id is required" }
            val visit = decodeRequired(owner, visitId)
            require(visit.deletedUtc == null) { "Gym visit is already deleted" }
            if (workout in visit.workoutIds) {
                visit
            } else {
                persist(
                    owner,
                    visit.copy(workoutIds = visit.workoutIds + workout, revision = visit.revision + 1)
                )
            }
        }

    suspend fun delete(owner: OwnerId, id: String): GymVisit = database.withTransaction {
        val visit = decodeRequired(owner, id)
        require(visit.deletedUtc == null) { "Gym visit is already deleted" }
        val deleted = visit.copy(deletedUtc = now(), revision = visit.revision + 1)
        database.gymVisits().upsert(deleted.toEntity())
        val operationUuid = newId()
        database.tombstones().upsert(
            TombstoneEntity(
                ownerUID = owner.uid,
                entityType = ENTITY_TYPE,
                entityId = id,
                deletedUtc = deleted.deletedUtc!!.toEpochMilliseconds(),
                operationUuid = operationUuid,
            )
        )
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = id,
            entityType = ENTITY_TYPE,
            operationUuid = operationUuid,
            revision = deleted.revision,
            op = SyncEvent.Op.DELETE,
            payloadJson = durableJson.encodeToString(deleted),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
        deleted
    }

    /** Row write + immutable UPSERT event; callers wrap this in withTransaction. */
    private suspend fun persist(owner: OwnerId, visit: GymVisit): GymVisit {
        database.gymVisits().upsert(visit.toEntity())
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = visit.id,
            entityType = ENTITY_TYPE,
            operationUuid = newId(),
            revision = visit.revision,
            op = SyncEvent.Op.UPSERT,
            payloadJson = durableJson.encodeToString(visit),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
        return visit
    }

    private suspend fun decodeRequired(owner: OwnerId, id: String): GymVisit {
        val entity = database.gymVisits().byId(owner.uid, id)
            ?: throw NoSuchElementException("Gym visit not found for this owner")
        return decode(entity, owner.uid)
    }

    private fun decode(entity: GymVisitEntity, ownerUID: String): GymVisit {
        check(entity.ownerUID == ownerUID) { "Gym visit row owner does not match the requested owner" }
        val visit = try {
            durableJson.decodeFromString(GymVisit.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt gym visit payload", failure)
        }
        check(visit.id == entity.id && visit.ownerUID == ownerUID) {
            "Gym visit payload does not match its row identity"
        }
        return visit
    }

    private fun GymVisit.toEntity() = GymVisitEntity(
        id = id,
        ownerUID = ownerUID,
        startInstant = startInstant.toEpochMilliseconds(),
        endInstant = endInstant?.toEpochMilliseconds(),
        localAttendanceDate = localAttendanceDate,
        confirmed = confirmed,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
        deletedUtc = deletedUtc?.toEpochMilliseconds(),
    )

    companion object {
        const val ENTITY_TYPE = "gym_visit"
    }
}
