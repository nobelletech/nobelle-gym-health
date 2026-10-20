package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.OutdoorSessionEntity
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.model.OutdoorSession
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/**
 * Owner-scoped durable outdoor GPS sessions. A completed track is persisted
 * and enqueued for Drive sync in the same transaction — identical to the
 * manual-entry path in [HealthObservationRepository].
 */
class OutdoorSessionRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun get(owner: OwnerId, id: String): OutdoorSession? =
        database.outdoorSessions().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<OutdoorSession> =
        database.outdoorSessions().list(owner.uid).map { decode(it, owner.uid) }

    suspend fun upsert(owner: OwnerId, session: OutdoorSession): OutdoorSession = database.withTransaction {
        require(session.ownerUID == owner.uid) { "Outdoor session does not belong to the requested owner" }
        persist(owner, session)
        session
    }

    private suspend fun persist(owner: OwnerId, session: OutdoorSession) {
        database.outdoorSessions().upsert(session.toEntity())
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = session.id,
            entityType = ENTITY_TYPE,
            operationUuid = newId(),
            revision = session.revision,
            op = SyncEvent.Op.UPSERT,
            payloadJson = durableJson.encodeToString(session),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
    }

    suspend fun delete(owner: OwnerId, id: String): OutdoorSession = database.withTransaction {
        val entity = database.outdoorSessions().byId(owner.uid, id)
            ?: throw NoSuchElementException("Outdoor session not found for this owner")
        val session = decode(entity, owner.uid)
        require(session.deletedUtc == null) { "Outdoor session is already deleted" }
        val deleted = session.copy(
            deletedUtc = now(),
            updatedUtc = now(),
            revision = session.revision + 1,
        )
        database.outdoorSessions().upsert(deleted.toEntity())
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

    private fun decode(entity: OutdoorSessionEntity, ownerUID: String): OutdoorSession {
        check(entity.ownerUID == ownerUID) { "Outdoor session row owner does not match the requested owner" }
        val session = try {
            durableJson.decodeFromString(OutdoorSession.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt outdoor session payload", failure)
        }
        check(session.id == entity.id && session.ownerUID == ownerUID) {
            "Outdoor session payload does not match its row identity"
        }
        return session
    }

    private fun OutdoorSession.toEntity() = OutdoorSessionEntity(
        id = id,
        ownerUID = ownerUID,
        activity = activity,
        startedUtc = startedUtc.toEpochMilliseconds(),
        endedUtc = endedUtc.toEpochMilliseconds(),
        durationSec = durationSec,
        distanceMeters = distanceMeters,
        pointCount = pointCount,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
        createdUtc = createdUtc.toEpochMilliseconds(),
        updatedUtc = updatedUtc?.toEpochMilliseconds(),
        deletedUtc = deletedUtc?.toEpochMilliseconds(),
    )

    companion object {
        const val ENTITY_TYPE = "outdoor_session"
    }
}
