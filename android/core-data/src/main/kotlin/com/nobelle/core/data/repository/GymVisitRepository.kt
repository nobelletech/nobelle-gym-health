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
        visit
    }

    suspend fun delete(owner: OwnerId, id: String): GymVisit = database.withTransaction {
        val entity = database.gymVisits().byId(owner.uid, id)
            ?: throw NoSuchElementException("Gym visit not found for this owner")
        val visit = decode(entity, owner.uid)
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
