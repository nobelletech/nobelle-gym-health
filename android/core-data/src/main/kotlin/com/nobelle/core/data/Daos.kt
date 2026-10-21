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

    @Query("DELETE FROM workout_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface WorkoutSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE ownerUID = :ownerUID AND sessionId = :sessionId")
    suspend fun bySession(ownerUID: String, sessionId: String): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE ownerUID = :ownerUID AND sessionId = :sessionId")
    suspend fun deleteBySession(ownerUID: String, sessionId: String)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // idempotent by operationUuid
    suspend fun enqueue(op: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE ownerUID = :ownerUID AND state = 'PENDING' ORDER BY createdUtc LIMIT :limit")
    suspend fun pending(ownerUID: String, limit: Int = 100): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerUID = :ownerUID AND operationUuid = :operationUuid")
    suspend fun byUuid(ownerUID: String, operationUuid: String): SyncOutboxEntity?

    /** True once the op has been domain-integrated — the download dedupe check. */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE ownerUID = :ownerUID AND operationUuid = :operationUuid AND state = 'APPLIED')")
    suspend fun appliedExists(ownerUID: String, operationUuid: String): Boolean

    @Query("UPDATE sync_outbox SET state = :state, attempts = attempts + 1, lastError = :error WHERE ownerUID = :ownerUID AND operationUuid = :id")
    suspend fun markState(ownerUID: String, id: String, state: String, error: String? = null): Int

    /** APPLIED records the download dedupe marker; it is not an upload retry,
     *  so unlike markState it must not bump attempts or lastError. */
    @Query("UPDATE sync_outbox SET state = 'APPLIED' WHERE ownerUID = :ownerUID AND operationUuid = :operationUuid")
    suspend fun markApplied(ownerUID: String, operationUuid: String): Int
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

    @Query("""SELECT * FROM fasting_sessions
        WHERE ownerUID=:ownerUID AND actualStartInstant=:startMs AND actualEndInstant=:endMs
        AND status='COMPLETED' AND deletedUtc IS NULL
        ORDER BY revision DESC LIMIT 1""")
    suspend fun completedByWindow(ownerUID: String, startMs: Long, endMs: Long): FastingSessionEntity?

    @Query("SELECT * FROM fasting_sessions WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY actualStartInstant DESC")
    suspend fun list(ownerUID: String): List<FastingSessionEntity>

    @Query("DELETE FROM fasting_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface FastingScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: FastingScheduleEntity)

    @Query("SELECT * FROM fasting_schedules WHERE ownerUID = :ownerUID AND enabled = 1 LIMIT 1")
    suspend fun enabled(ownerUID: String): FastingScheduleEntity?

    @Query("SELECT * FROM fasting_schedules WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): FastingScheduleEntity?

    @Query("SELECT * FROM fasting_schedules WHERE ownerUID = :ownerUID")
    suspend fun list(ownerUID: String): List<FastingScheduleEntity>

    @Query("DELETE FROM fasting_schedules WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface HealthObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(o: HealthObservationEntity)

    @Query("SELECT * FROM health_observations WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): HealthObservationEntity?

    @Query("SELECT * FROM health_observations WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY measuredUtc DESC")
    suspend fun list(ownerUID: String): List<HealthObservationEntity>

    @Query("DELETE FROM health_observations WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface WatchWorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(w: WatchWorkoutEntity)

    @Query("SELECT * FROM watch_workouts WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): WatchWorkoutEntity?

    @Query("SELECT * FROM watch_workouts WHERE ownerUID = :ownerUID ORDER BY startedUtc DESC")
    suspend fun list(ownerUID: String): List<WatchWorkoutEntity>

    @Query("DELETE FROM watch_workouts WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface OutdoorSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: OutdoorSessionEntity)

    @Query("SELECT * FROM outdoor_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): OutdoorSessionEntity?

    @Query("SELECT * FROM outdoor_sessions WHERE ownerUID = :ownerUID AND deletedUtc IS NULL ORDER BY startedUtc DESC")
    suspend fun list(ownerUID: String): List<OutdoorSessionEntity>

    @Query("DELETE FROM outdoor_sessions WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface WorkoutTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: WorkoutTemplateEntity)

    @Query("SELECT * FROM workout_templates WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun byId(ownerUID: String, id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_templates WHERE ownerUID = :ownerUID ORDER BY name, id")
    suspend fun list(ownerUID: String): List<WorkoutTemplateEntity>

    @Query("DELETE FROM workout_templates WHERE ownerUID = :ownerUID AND id = :id")
    suspend fun hardDelete(ownerUID: String, id: String)
}

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TombstoneEntity)

    @Query("SELECT * FROM deletion_tombstones WHERE ownerUID = :ownerUID AND entityType = :entityType AND entityId = :entityId")
    suspend fun byEntity(ownerUID: String, entityType: String, entityId: String): TombstoneEntity?
}
