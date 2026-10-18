package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.HealthObservationEntity
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/** Owner-scoped durable health observations; payload keeps the full domain JSON. */
class HealthObservationRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun get(owner: OwnerId, id: String): HealthObservation? =
        database.healthObservations().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<HealthObservation> =
        database.healthObservations().list(owner.uid).map { decode(it, owner.uid) }

    suspend fun upsert(owner: OwnerId, observation: HealthObservation): HealthObservation = database.withTransaction {
        require(observation.ownerUID == owner.uid) { "Health observation does not belong to the requested owner" }
        database.healthObservations().upsert(observation.toEntity())
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = observation.id,
            entityType = ENTITY_TYPE,
            operationUuid = newId(),
            revision = observation.revision,
            op = SyncEvent.Op.UPSERT,
            payloadJson = durableJson.encodeToString(observation),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
        observation
    }

    suspend fun delete(owner: OwnerId, id: String): HealthObservation = database.withTransaction {
        val entity = database.healthObservations().byId(owner.uid, id)
            ?: throw NoSuchElementException("Health observation not found for this owner")
        val observation = decode(entity, owner.uid)
        require(observation.deletedUtc == null) { "Health observation is already deleted" }
        val deleted = observation.copy(deletedUtc = now(), revision = observation.revision + 1)
        database.healthObservations().upsert(deleted.toEntity())
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

    private fun decode(entity: HealthObservationEntity, ownerUID: String): HealthObservation {
        check(entity.ownerUID == ownerUID) { "Health observation row owner does not match the requested owner" }
        val observation = try {
            durableJson.decodeFromString(HealthObservation.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt health observation payload", failure)
        }
        check(observation.id == entity.id && observation.ownerUID == ownerUID) {
            "Health observation payload does not match its row identity"
        }
        return observation
    }

    private fun HealthObservation.toEntity() = HealthObservationEntity(
        id = id,
        ownerUID = ownerUID,
        marker = marker,
        measuredUtc = measuredUtc.toEpochMilliseconds(),
        sourceNamespace = source?.sourceNamespace,
        sourceRecordId = source?.sourceRecordId,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
        deletedUtc = deletedUtc?.toEpochMilliseconds(),
    )

    companion object {
        const val ENTITY_TYPE = "health_observation"
    }
}
