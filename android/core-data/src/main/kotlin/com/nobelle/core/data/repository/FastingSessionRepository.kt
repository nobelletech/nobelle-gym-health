package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.FastingSessionEntity
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/** Owner-scoped durable fasting sessions. At most one ACTIVE fast per owner. */
class FastingSessionRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun get(owner: OwnerId, id: String): FastingSession? =
        database.fastingSessions().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<FastingSession> =
        database.fastingSessions().list(owner.uid).map { decode(it, owner.uid) }

    suspend fun active(owner: OwnerId): FastingSession? =
        database.fastingSessions().active(owner.uid)?.let { decode(it, owner.uid) }

    /** Persists the fast as given — actual instants and zones are never recomputed. */
    suspend fun upsert(owner: OwnerId, session: FastingSession): FastingSession = database.withTransaction {
        require(session.ownerUID == owner.uid) { "Fasting session does not belong to the requested owner" }
        database.fastingSessions().upsert(session.toEntity())
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
        session
    }

    suspend fun delete(owner: OwnerId, id: String): FastingSession = database.withTransaction {
        val entity = database.fastingSessions().byId(owner.uid, id)
            ?: throw NoSuchElementException("Fasting session not found for this owner")
        val session = decode(entity, owner.uid)
        require(session.deletedUtc == null) { "Fasting session is already deleted" }
        val deleted = session.copy(deletedUtc = now(), revision = session.revision + 1)
        database.fastingSessions().upsert(deleted.toEntity())
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

    private fun decode(entity: FastingSessionEntity, ownerUID: String): FastingSession {
        check(entity.ownerUID == ownerUID) { "Fasting session row owner does not match the requested owner" }
        val session = try {
            durableJson.decodeFromString(FastingSession.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt fasting session payload", failure)
        }
        check(session.id == entity.id && session.ownerUID == ownerUID) {
            "Fasting session payload does not match its row identity"
        }
        return session
    }

    private fun FastingSession.toEntity() = FastingSessionEntity(
        id = id,
        ownerUID = ownerUID,
        actualStartInstant = actualStartInstant.toEpochMilliseconds(),
        actualEndInstant = actualEndInstant?.toEpochMilliseconds(),
        status = status.name,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
        deletedUtc = deletedUtc?.toEpochMilliseconds(),
    )

    companion object {
        const val ENTITY_TYPE = "fasting_session"
    }
}
