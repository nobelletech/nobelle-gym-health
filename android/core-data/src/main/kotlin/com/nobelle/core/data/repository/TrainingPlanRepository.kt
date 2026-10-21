package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.data.TrainingPlanEntity
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.TrainingPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString

/**
 * Weekly training plans and their sync operations commit together, scoped to
 * the owner. A status flip on one planned session is an UPSERT of the whole
 * plan — the plan is the synced entity, so revision semantics match the other
 * repositories.
 */
class TrainingPlanRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend fun get(owner: OwnerId, id: String): TrainingPlan? =
        database.trainingPlans().byId(owner.uid, id)?.let { decode(it, owner.uid) }

    /** All plans, newest week first. */
    suspend fun list(owner: OwnerId): List<TrainingPlan> =
        database.trainingPlans().list(owner.uid).map { decode(it, owner.uid) }

    /** The plan(s) whose weekStartDate is the Monday of the current week. */
    suspend fun currentWeek(owner: OwnerId): List<TrainingPlan> {
        val today = now().toLocalDateTime(zone()).date
        val monday = LocalDate.ofEpochDay(today.toEpochDays().toLong())
            .with(DayOfWeek.MONDAY).toString()
        return database.trainingPlans().byWeek(owner.uid, monday).map { decode(it, owner.uid) }
    }

    suspend fun upsert(owner: OwnerId, plan: TrainingPlan): TrainingPlan = database.withTransaction {
        require(plan.ownerUID == owner.uid) { "Plan does not belong to the requested owner" }
        require(database.tombstones().byEntity(owner.uid, ENTITY_TYPE, plan.id) == null) {
            "Deleted plans cannot be restored; create a new plan"
        }
        val weekStart = try {
            LocalDate.parse(plan.weekStartDate)
        } catch (failure: Exception) {
            throw IllegalArgumentException("weekStartDate must be an ISO local date", failure)
        }
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) {
            "weekStartDate must be a Monday (ISO week start)"
        }
        require(plan.generatedBy.isNotBlank()) { "generatedBy must record the plan's provenance" }
        plan.plannedSessions.forEach { session ->
            require(session.ownerUID == owner.uid) {
                "Planned session does not belong to the requested owner"
            }
            val date = try {
                LocalDate.parse(session.localDate)
            } catch (failure: Exception) {
                throw IllegalArgumentException("Planned session localDate must be an ISO local date", failure)
            }
            require(!date.isBefore(weekStart) && date.isBefore(weekStart.plusDays(7))) {
                "Planned session date falls outside the plan's week"
            }
            require(session.category.isNotBlank()) { "Planned session needs a category" }
        }
        val existing = database.trainingPlans().byId(owner.uid, plan.id)
        val saved = plan.copy(revision = existing?.let { it.revision + 1 } ?: 1)
        database.trainingPlans().upsert(TrainingPlanEntity(
            ownerUID = owner.uid, id = saved.id, weekStartDate = saved.weekStartDate,
            sessionCount = saved.plannedSessions.size, generatedBy = saved.generatedBy,
            payloadJson = durableJson.encodeToString(saved), revision = saved.revision,
        ))
        enqueue(owner, saved, SyncEvent.Op.UPSERT, newId())
        saved
    }

    /**
     * Flips one planned session's status. The change lands as a single plan
     * UPSERT with a bumped revision — the plan is the synced unit.
     */
    suspend fun setPlannedSessionStatus(
        owner: OwnerId,
        planId: String,
        sessionId: String,
        status: PlannedSession.Status,
    ): TrainingPlan {
        val plan = get(owner, planId)
            ?: throw NoSuchElementException("Training plan not found for this owner")
        require(plan.plannedSessions.any { it.id == sessionId }) {
            "Planned session not found in this plan"
        }
        val edited = plan.copy(
            plannedSessions = plan.plannedSessions.map {
                if (it.id == sessionId) it.copy(status = status) else it
            },
        )
        return upsert(owner, edited)
    }

    suspend fun delete(owner: OwnerId, id: String): TrainingPlan = database.withTransaction {
        val plan = get(owner, id) ?: throw NoSuchElementException("Training plan not found for this owner")
        val deleted = plan.copy(revision = plan.revision + 1)
        val operationUuid = newId()
        database.tombstones().upsert(TombstoneEntity(
            ownerUID = owner.uid, entityType = ENTITY_TYPE, entityId = id,
            deletedUtc = now().toEpochMilliseconds(), operationUuid = operationUuid,
        ))
        database.trainingPlans().hardDelete(owner.uid, id)
        enqueue(owner, deleted, SyncEvent.Op.DELETE, operationUuid)
        deleted
    }

    private suspend fun enqueue(owner: OwnerId, plan: TrainingPlan, op: SyncEvent.Op, operationUuid: String) {
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION, accountNamespace = owner.uid,
            installationId = installationId, entityId = plan.id, entityType = ENTITY_TYPE,
            operationUuid = operationUuid, revision = plan.revision, op = op,
            payloadJson = durableJson.encodeToString(plan), utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
    }

    private fun decode(entity: TrainingPlanEntity, ownerUID: String): TrainingPlan {
        check(entity.ownerUID == ownerUID)
        val plan = try {
            durableJson.decodeFromString(TrainingPlan.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt training plan payload", failure)
        }
        check(plan.id == entity.id && plan.ownerUID == ownerUID) {
            "Training plan payload does not match its row identity"
        }
        return plan
    }

    companion object { const val ENTITY_TYPE = "training_plan" }
}
