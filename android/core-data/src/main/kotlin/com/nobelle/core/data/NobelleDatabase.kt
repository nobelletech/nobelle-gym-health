package com.nobelle.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Durable owner-scoped working store. Never installed with a destructive
 *  fallback: every upgrade is an explicit, data-preserving migration. */
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        GymVisitEntity::class,
        FastingSessionEntity::class,
        FastingScheduleEntity::class,
        HealthObservationEntity::class,
        WatchWorkoutEntity::class,
        OutdoorSessionEntity::class,
        SyncOutboxEntity::class,
        TombstoneEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NobelleDatabase : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun sessions(): SessionDao
    abstract fun workoutSets(): WorkoutSetDao
    abstract fun gymVisits(): GymVisitDao
    abstract fun fastingSessions(): FastingSessionDao
    abstract fun fastingSchedules(): FastingScheduleDao
    abstract fun healthObservations(): HealthObservationDao
    abstract fun watchWorkouts(): WatchWorkoutDao
    abstract fun outdoorSessions(): OutdoorSessionDao
    abstract fun tombstones(): TombstoneDao

    companion object {
        /** v1 -> v2: add workout_sets, owner/state indexes, and rebuild the
         *  session/visit/fasting/observation and tombstone tables with
         *  composite owner keys so INSERT OR REPLACE can never cross accounts.
         *  All v1 rows are preserved. Indexes are created only after the
         *  rebuilt tables are renamed, so none target dropped tables. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildWithCompositeOwnerKey(
                    db, "workout_sessions",
                    "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`startedUtc` INTEGER, `endedUtc` INTEGER, `sequence` INTEGER NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL",
                    "`ownerUID`, `id`, `state`, `startedUtc`, `endedUtc`, `sequence`, `payloadJson`, `revision`",
                )
                rebuildWithCompositeOwnerKey(
                    db, "gym_visits",
                    "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `startInstant` INTEGER NOT NULL, " +
                        "`endInstant` INTEGER, `localAttendanceDate` TEXT NOT NULL, `confirmed` INTEGER NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL, `deletedUtc` INTEGER",
                    "`ownerUID`, `id`, `startInstant`, `endInstant`, `localAttendanceDate`, `confirmed`, `payloadJson`, `revision`, `deletedUtc`",
                )
                rebuildWithCompositeOwnerKey(
                    db, "fasting_sessions",
                    "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `actualStartInstant` INTEGER NOT NULL, " +
                        "`actualEndInstant` INTEGER, `status` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                        "`revision` INTEGER NOT NULL, `deletedUtc` INTEGER",
                    "`ownerUID`, `id`, `actualStartInstant`, `actualEndInstant`, `status`, `payloadJson`, `revision`, `deletedUtc`",
                )
                rebuildWithCompositeOwnerKey(
                    db, "health_observations",
                    "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `marker` TEXT NOT NULL, " +
                        "`measuredUtc` INTEGER NOT NULL, `sourceNamespace` TEXT, `sourceRecordId` TEXT, " +
                        "`payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL, `deletedUtc` INTEGER",
                    "`ownerUID`, `id`, `marker`, `measuredUtc`, `sourceNamespace`, `sourceRecordId`, `payloadJson`, `revision`, `deletedUtc`",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_sessions_ownerUID_state` " +
                        "ON `workout_sessions` (`ownerUID`, `state`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gym_visits_ownerUID_localAttendanceDate` " +
                        "ON `gym_visits` (`ownerUID`, `localAttendanceDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fasting_sessions_ownerUID_status` " +
                        "ON `fasting_sessions` (`ownerUID`, `status`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_observations_ownerUID_marker` " +
                        "ON `health_observations` (`ownerUID`, `marker`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                        "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`completedUtc` INTEGER, `payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerUID`, `id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_sets_ownerUID_sessionId` " +
                        "ON `workout_sets` (`ownerUID`, `sessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_outbox_ownerUID_state_createdUtc` " +
                        "ON `sync_outbox` (`ownerUID`, `state`, `createdUtc`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `deletion_tombstones_new` (" +
                        "`ownerUID` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, " +
                        "`deletedUtc` INTEGER NOT NULL, `operationUuid` TEXT NOT NULL, " +
                        "PRIMARY KEY(`ownerUID`, `entityType`, `entityId`))"
                )
                db.execSQL(
                    "INSERT INTO `deletion_tombstones_new` (`ownerUID`, `entityType`, `entityId`, `deletedUtc`, `operationUuid`) " +
                        "SELECT `ownerUID`, `entityType`, `entityId`, `deletedUtc`, `operationUuid` FROM `deletion_tombstones`"
                )
                db.execSQL("DROP TABLE `deletion_tombstones`")
                db.execSQL("ALTER TABLE `deletion_tombstones_new` RENAME TO `deletion_tombstones`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_deletion_tombstones_operationUuid` " +
                        "ON `deletion_tombstones` (`operationUuid`)"
                )
            }
        }

        /** v2 -> v3: durable fasting schedules (opt-in eating-window planning
         *  context; never auto-starts anything). Pure additive table+index. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fasting_schedules` (" +
                        "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerUID`, `id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fasting_schedules_ownerUID_enabled` " +
                        "ON `fasting_schedules` (`ownerUID`, `enabled`)"
                )
            }
        }

        /** v3 -> v4: durable store for workouts relayed from a paired watch.
         *  Pure additive table+index. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_workouts` (" +
                        "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `activity` TEXT NOT NULL, " +
                        "`startedUtc` INTEGER NOT NULL, `endedUtc` INTEGER NOT NULL, " +
                        "`durationSec` INTEGER NOT NULL, `hrSampleCount` INTEGER NOT NULL, " +
                        "`avgBpm` REAL, `maxBpm` REAL, `payloadJson` TEXT NOT NULL, " +
                        "`revision` INTEGER NOT NULL, PRIMARY KEY(`ownerUID`, `id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_workouts_ownerUID_startedUtc` " +
                        "ON `watch_workouts` (`ownerUID`, `startedUtc`)"
                )
            }
        }

        /** v4 -> v5: durable store for phone-recorded outdoor GPS sessions.
         *  Pure additive table+index. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outdoor_sessions` (" +
                        "`ownerUID` TEXT NOT NULL, `id` TEXT NOT NULL, `activity` TEXT NOT NULL, " +
                        "`startedUtc` INTEGER NOT NULL, `endedUtc` INTEGER NOT NULL, " +
                        "`durationSec` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, " +
                        "`pointCount` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, " +
                        "`revision` INTEGER NOT NULL, `createdUtc` INTEGER NOT NULL, " +
                        "`updatedUtc` INTEGER, `deletedUtc` INTEGER, " +
                        "PRIMARY KEY(`ownerUID`, `id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outdoor_sessions_ownerUID_startedUtc` " +
                        "ON `outdoor_sessions` (`ownerUID`, `startedUtc`)"
                )
            }
        }

        /** Rebuilds [table] as `<table>_new` with `PRIMARY KEY(ownerUID, id)`,
         *  copies every v1 column, then swaps the tables. */
        private fun rebuildWithCompositeOwnerKey(
            db: SupportSQLiteDatabase,
            table: String,
            columnDefs: String,
            columnList: String,
        ) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${table}_new` ($columnDefs, PRIMARY KEY(`ownerUID`, `id`))"
            )
            db.execSQL("INSERT INTO `${table}_new` ($columnList) SELECT $columnList FROM `$table`")
            db.execSQL("DROP TABLE `$table`")
            db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
        }
    }
}
