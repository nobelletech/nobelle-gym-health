package com.nobelle.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Every query is owner-scoped: no DAO method may read, update or dequeue
 *  rows belonging to another signed-in account. */

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE ownerUID = :ownerUID AND state IN ('ACTIVE','PAUSED') ORDER BY startedUtc DESC LIMIT 1")
    suspend fun activeSession(ownerUID: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE ownerUID = :ownerUID ORDER BY startedUtc DESC")
    suspend fun list(ownerUID: String): List<WorkoutSessionEntity>
}

@Dao
interface WorkoutSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE ownerUID = :ownerUID AND sessionId = :sessionId")
    suspend fun bySession(ownerUID: String, sessionId: String): List<WorkoutSetEntity>
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // idempotent by operationUuid
    suspend fun enqueue(op: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE ownerUID = :ownerUID AND state = 'PENDING' ORDER BY createdUtc LIMIT :limit")
    suspend fun pending(ownerUID: String, limit: Int = 100): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET state = :state, attempts = attempts + 1, lastError = :error WHERE ownerUID = :ownerUID AND operationUuid = :id")
    suspend fun markState(ownerUID: String, id: String, state: String, error: String? = null): Int
}

@Dao
interface GymVisitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: GymVisitEntity)

    @Query("SELECT * FROM gym_visits WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): GymVisitEntity?

    @Query("SELECT * FROM gym_visits WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY startInstant DESC")
    suspend fun list(ownerUID: String): List<GymVisitEntity>

    @Query("DELETE FROM gym_visits WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface FastingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: FastingSessionEntity)

    @Query("SELECT * FROM fasting_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): FastingSessionEntity?

    @Query("SELECT * FROM fasting_sessions WHERE ownerUID = :ownerUID AND status = 'ACTIVE' AND deletedUtc IS NULL")
    suspend fun active(ownerUID: String): FastingSessionEntity?

    @Query("SELECT * FROM fasting_sessions WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY actualStartInstant DESC")
    suspend fun list(ownerUID: String): List<FastingSessionEntity>
}

@Dao
interface HealthObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(o: HealthObservationEntity)

    @Query("SELECT * FROM health_observations WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): HealthObservationEntity?

    @Query("SELECT * FROM health_observations WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY measuredUtc DESC")
    suspend fun list(ownerUID: String): List<HealthObservationEntity>
}

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TombstoneEntity)

    @Query("SELECT * FROM deletion_tombstones WHERE ownerUID = :ownerUID AND entityType = :entityType AND entityId = :entityId")
    suspend fun byEntity(ownerUID: String, entityType: String, entityId: String): TombstoneEntity?
}
