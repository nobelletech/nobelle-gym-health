package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.data.WorkoutTemplateEntity
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.WorkoutTemplate
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/** User templates and their sync operations commit together, scoped to the owner. */
class WorkoutTemplateRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun get(owner: OwnerId, id: String): WorkoutTemplate? =
        database.workoutTemplates().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<WorkoutTemplate> =
        database.workoutTemplates().list(owner.uid).map { decode(it, owner.uid) }

    suspend fun upsert(owner: OwnerId, template: WorkoutTemplate): WorkoutTemplate = database.withTransaction {
        require(template.ownerUID == owner.uid) { "Template does not belong to the requested owner" }
        require(database.tombstones().byEntity(owner.uid, ENTITY_TYPE, template.id) == null) {
            "Deleted templates cannot be restored; create a new template"
        }
        require(template.name.isNotBlank() && template.category.isNotBlank())
        require(template.exercises.isNotEmpty())
        require(template.exercises.all {
            it.exerciseId.isNotBlank() && it.sets > 0 && it.restSec >= 0 &&
                (((it.reps ?: 0) > 0 && it.durationSec == null) ||
                    ((it.durationSec ?: 0) > 0 && it.reps == null))
        })
        val existing = database.workoutTemplates().byId(owner.uid, template.id)
        val saved = template.copy(revision = existing?.let { it.revision + 1 } ?: 1)
        database.workoutTemplates().upsert(WorkoutTemplateEntity(
            ownerUID = owner.uid, id = saved.id, name = saved.name, category = saved.category,
            payloadJson = durableJson.encodeToString(saved), revision = saved.revision,
        ))
        enqueue(owner, saved, SyncEvent.Op.UPSERT, newId())
        saved
    }

    suspend fun delete(owner: OwnerId, id: String): WorkoutTemplate = database.withTransaction {
        val template = get(owner, id) ?: throw NoSuchElementException("Template not found for this owner")
        val deleted = template.copy(revision = template.revision + 1)
        val operationUuid = newId()
        database.tombstones().upsert(TombstoneEntity(
            ownerUID = owner.uid, entityType = ENTITY_TYPE, entityId = id,
            deletedUtc = now().toEpochMilliseconds(), operationUuid = operationUuid,
        ))
        database.workoutTemplates().hardDelete(owner.uid, id)
        enqueue(owner, deleted, SyncEvent.Op.DELETE, operationUuid)
        deleted
    }

    private suspend fun enqueue(owner: OwnerId, template: WorkoutTemplate, op: SyncEvent.Op, operationUuid: String) {
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION, accountNamespace = owner.uid,
            installationId = installationId, entityId = template.id, entityType = ENTITY_TYPE,
            operationUuid = operationUuid, revision = template.revision, op = op,
            payloadJson = durableJson.encodeToString(template), utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
    }

    private fun decode(entity: WorkoutTemplateEntity, ownerUID: String): WorkoutTemplate {
        check(entity.ownerUID == ownerUID)
        val template = try {
            durableJson.decodeFromString(WorkoutTemplate.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt workout template payload", failure)
        }
        check(template.id == entity.id && template.ownerUID == ownerUID) {
            "Workout template payload does not match its row identity"
        }
        return template
    }

    companion object { const val ENTITY_TYPE = "workout_template" }
}
