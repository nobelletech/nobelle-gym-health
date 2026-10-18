package com.nobelle.core.data.repository

import androidx.room.withTransaction
import com.nobelle.core.data.FastingScheduleEntity
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.core.model.FastingSchedule
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString

/** Owner-scoped durable fasting schedules. Opt-in only: nothing here starts,
 *  ends or evaluates an actual fast — the schedule is planning context. */
class FastingScheduleRepository(
    private val database: NobelleDatabase,
    private val installationId: String,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun getEnabled(owner: OwnerId): FastingSchedule? =
        database.fastingSchedules().enabled(owner.uid)?.let { decode(it, owner.uid) }

    suspend fun list(owner: OwnerId): List<FastingSchedule> =
        database.fastingSchedules().list(owner.uid).map { decode(it, owner.uid) }

    /**
     * Enables one template schedule, transactionally retiring any current
     * enabled schedule (kept as history). Re-enabling the identical
     * template/start is a no-op with no extra outbox operation.
     */
    suspend fun enable(
        owner: OwnerId,
        fastingHours: Int,
        startLocalTime: String,
    ): FastingSchedule = database.withTransaction {
        require(FastingSchedule.TEMPLATES.any { it.first == fastingHours }) {
            "Unknown fasting template"
        }
        val parsedStart = try {
            LocalTime.parse(startLocalTime.trim())
        } catch (failure: Exception) {
            throw IllegalArgumentException("startLocalTime must be HH:mm")
        }
        val normalizedStart = "%02d:%02d".format(parsedStart.hour, parsedStart.minute)
        val current = database.fastingSchedules().enabled(owner.uid)?.let { decode(it, owner.uid) }
        if (current != null &&
            current.fastingHours == fastingHours && current.startLocalTime == normalizedStart
        ) {
            current
        } else {
            current?.let {
                persist(owner, it.copy(enabled = false, revision = it.revision + 1))
            }
            persist(
                owner,
                FastingSchedule(
                    id = newId(),
                    ownerUID = owner.uid,
                    enabled = true,
                    fastingHours = fastingHours,
                    eatingHours = 24 - fastingHours,
                    startLocalTime = normalizedStart,
                    daysOfWeek = (1..7).toList(),
                    timeZonePolicy = "FOLLOW_DEVICE",
                    exceptions = emptyList(),
                    revision = 1,
                )
            )
        }
    }

    /** Turns the tracker off. Schedule and session history are kept. */
    suspend fun disable(owner: OwnerId): FastingSchedule? = database.withTransaction {
        val current = database.fastingSchedules().enabled(owner.uid)?.let { decode(it, owner.uid) }
            ?: return@withTransaction null
        persist(owner, current.copy(enabled = false, revision = current.revision + 1))
    }

    /** Row write + immutable UPSERT event; callers wrap this in withTransaction. */
    private suspend fun persist(owner: OwnerId, schedule: FastingSchedule): FastingSchedule {
        database.fastingSchedules().upsert(schedule.toEntity())
        val event = SyncEvent(
            schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
            accountNamespace = owner.uid,
            installationId = installationId,
            entityId = schedule.id,
            entityType = ENTITY_TYPE,
            operationUuid = newId(),
            revision = schedule.revision,
            op = SyncEvent.Op.UPSERT,
            payloadJson = durableJson.encodeToString(schedule),
            utc = now(),
        )
        database.outbox().enqueue(event.toOutboxEntity(now().toEpochMilliseconds()))
        return schedule
    }

    private fun decode(entity: FastingScheduleEntity, ownerUID: String): FastingSchedule {
        check(entity.ownerUID == ownerUID) { "Fasting schedule row owner does not match the requested owner" }
        val schedule = try {
            durableJson.decodeFromString(FastingSchedule.serializer(), entity.payloadJson)
        } catch (failure: Exception) {
            throw IllegalStateException("Corrupt fasting schedule payload", failure)
        }
        check(schedule.id == entity.id && schedule.ownerUID == ownerUID) {
            "Fasting schedule payload does not match its row identity"
        }
        return schedule
    }

    private fun FastingSchedule.toEntity() = FastingScheduleEntity(
        id = id,
        ownerUID = ownerUID,
        enabled = enabled,
        payloadJson = durableJson.encodeToString(this),
        revision = revision,
    )

    companion object {
        const val ENTITY_TYPE = "fasting_schedule"
    }
}
