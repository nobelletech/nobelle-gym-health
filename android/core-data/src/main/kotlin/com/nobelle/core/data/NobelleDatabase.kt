package com.nobelle.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Durable session + outbox storage (§12/§13). High-rate samples are chunked
 *  separately; this DB holds session state and pending sync operations. */

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val ownerUID: String,
    val state: String,
    val startedUtc: Long?,
    val endedUtc: Long?,
    val sequence: Long,
    val payloadJson: String,
    val revision: Long,
)

@Entity(tableName = "gym_visits")
data class GymVisitEntity(
    @PrimaryKey val id: String,
    val ownerUID: String,
    val startInstant: Long,
    val endInstant: Long?,
    val localAttendanceDate: String,
    val confirmed: Boolean,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

@Entity(tableName = "fasting_sessions")
data class FastingSessionEntity(
    @PrimaryKey val id: String,
    val ownerUID: String,
    val actualStartInstant: Long,
    val actualEndInstant: Long?,
    val status: String,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

@Entity(tableName = "health_observations")
data class HealthObservationEntity(
    @PrimaryKey val id: String,
    val ownerUID: String,
    val marker: String,
    val measuredUtc: Long,
    val sourceNamespace: String?,
    val sourceRecordId: String?,
    val payloadJson: String,
    val revision: Long,
    val deletedUtc: Long?,
)

@Entity(tableName = "sync_outbox")
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

@Entity(tableName = "deletion_tombstones")
data class TombstoneEntity(
    @PrimaryKey val entityId: String,
    val entityType: String,
    val ownerUID: String,
    val deletedUtc: Long,
    val operationUuid: String,
)

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // idempotent by operationUuid
    suspend fun enqueue(op: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE state = 'PENDING' ORDER BY createdUtc LIMIT :limit")
    suspend fun pending(limit: Int = 100): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET state = :state, attempts = attempts + 1, lastError = :error WHERE operationUuid = :id")
    suspend fun markState(id: String, state: String, error: String? = null)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun byId(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE state IN ('ACTIVE','PAUSED') LIMIT 1")
    suspend fun activeSession(): WorkoutSessionEntity?
}

@Database(
    entities = [
        WorkoutSessionEntity::class,
        GymVisitEntity::class,
        FastingSessionEntity::class,
        HealthObservationEntity::class,
        SyncOutboxEntity::class,
        TombstoneEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NobelleDatabase : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun sessions(): SessionDao
}
