package com.nobelle.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable session + outbox storage (§12/§13). High-rate samples are chunked
 *  separately; this DB holds session state and pending sync operations.
 *  Every row carries ownerUID; all access is owner-scoped so accounts share
 *  the device without seeing each other's records. */

@Entity(
    tableName = "workout_sessions",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "state"])],
)
data class WorkoutSessionEntity(
    val ownerUID: String,
    val id: String,
    val state: String,
    val startedUtc: Long?,
    val endedUtc: Long?,
    val sequence: Long,
    val payloadJson: String,
    val revision: Long,
)

@Entity(
    tableName = "workout_sets",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "sessionId"])],
)
data class WorkoutSetEntity(
    val ownerUID: String,
    val id: String,
    val sessionId: String,
    val completedUtc: Long?,
    val payloadJson: String,
    val revision: Long,
)

@Entity(
    tableName = "gym_visits",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "localAttendanceDate"])],
)
data class GymVisitEntity(
    val ownerUID: String,
    val id: String,
    val startInstant: Long,
    val endInstant: Long?,
    val localAttendanceDate: String,
    val confirmed: Boolean,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

@Entity(
    tableName = "fasting_sessions",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "status"])],
)
data class FastingSessionEntity(
    val ownerUID: String,
    val id: String,
    val actualStartInstant: Long,
    val actualEndInstant: Long?,
    val status: String,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

@Entity(
    tableName = "fasting_schedules",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "enabled"])],
)
data class FastingScheduleEntity(
    val ownerUID: String,
    val id: String,
    val enabled: Boolean,
    val payloadJson: String,
    val revision: Long,
)

@Entity(
    tableName = "health_observations",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "marker"])],
)
data class HealthObservationEntity(
    val ownerUID: String,
    val id: String,
    val marker: String,
    val measuredUtc: Long,
    val sourceNamespace: String?,
    val sourceRecordId: String?,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

/**
 * Workouts relayed from a paired watch. The watch sends no deletes and no
 * payload fields beyond the summary below; payloadJson keeps the canonical
 * wire JSON so nothing the watch reported is lost.
 */
@Entity(
    tableName = "watch_workouts",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "startedUtc"])],
)
data class WatchWorkoutEntity(
    val ownerUID: String,
    val id: String,
    val activity: String,
    val startedUtc: Long,
    val endedUtc: Long,
    val durationSec: Long,
    val hrSampleCount: Int,
    val avgBpm: Double?,
    val maxBpm: Double?,
    val payloadJson: String,
    val revision: Long,
)

/**
 * Outdoor GPS sessions recorded on the phone (Coach "Outdoor"). Route points
 * live inside payloadJson; the columns keep the display summary so lists
 * never decode the wire JSON.
 */
@Entity(
    tableName = "outdoor_sessions",
    primaryKeys = ["ownerUID", "id"],
    indices = [Index(value = ["ownerUID", "startedUtc"])],
)
data class OutdoorSessionEntity(
    val ownerUID: String,
    val id: String,
    val activity: String,
    val startedUtc: Long,
    val endedUtc: Long,
    val durationSec: Long,
    val distanceMeters: Double,
    val pointCount: Int,
    val payloadJson: String,
    val revision: Long,
    val createdUtc: Long,
    val updatedUtc: Long?,
    val deletedUtc: Long?,
)

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["ownerUID", "state", "createdUtc"])],
)
data class SyncOutboxEntity(
    @PrimaryKey val operationUuid: String,
    val ownerUID: String,
    val entityId: String,
    val entityType: String,
    val state: String,
    val attempts: Int,
    val lastError: String?,
    val createdUtc: Long,
    val payloadJson: String,
)

@Entity(
    tableName = "deletion_tombstones",
    primaryKeys = ["ownerUID", "entityType", "entityId"],
    indices = [Index(value = ["operationUuid"], unique = true)],
)
data class TombstoneEntity(
    val ownerUID: String,
    val entityType: String,
    val entityId: String,
    val deletedUtc: Long,
    val operationUuid: String,
)
