package com.nobelle.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NobelleDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesDataAndAllowsCompositeOwnerKeys() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO workout_sessions (id, ownerUID, state, startedUtc, endedUtc, sequence, payloadJson, revision) " +
                    "VALUES ('s1', 'owner-a', 'ACTIVE', 1700000000000, NULL, 3, '{\"id\":\"s1\"}', 2)"
            )
            execSQL(
                "INSERT INTO gym_visits (id, ownerUID, startInstant, endInstant, localAttendanceDate, confirmed, payloadJson, revision, deletedUtc) " +
                    "VALUES ('v1', 'owner-a', 1700000000000, 1700003600000, '2023-11-14', 1, '{\"id\":\"v1\"}', 1, NULL)"
            )
            execSQL(
                "INSERT INTO fasting_sessions (id, ownerUID, actualStartInstant, actualEndInstant, status, payloadJson, revision, deletedUtc) " +
                    "VALUES ('f1', 'owner-a', 1700000000000, NULL, 'ACTIVE', '{\"id\":\"f1\"}', 1, NULL)"
            )
            execSQL(
                "INSERT INTO health_observations (id, ownerUID, marker, measuredUtc, sourceNamespace, sourceRecordId, payloadJson, revision, deletedUtc) " +
                    "VALUES ('h1', 'owner-a', 'weight', 1700000000000, 'hc', 'rec-1', '{\"id\":\"h1\"}', 1, NULL)"
            )
            execSQL(
                "INSERT INTO deletion_tombstones (entityId, entityType, ownerUID, deletedUtc, operationUuid) " +
                    "VALUES ('v1', 'gym_visit', 'owner-a', 1700000001000, 'op-t1')"
            )
            close()
        }

        // Validates the migrated DB byte-for-byte against Room's exported v2 schema.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, NobelleDatabase.MIGRATION_1_2)

        db.query("SELECT ownerUID, state, sequence, payloadJson FROM workout_sessions WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("owner-a", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
            assertEquals(3L, cursor.getLong(2))
            assertEquals("{\"id\":\"s1\"}", cursor.getString(3))
        }
        db.query("SELECT ownerUID, localAttendanceDate, confirmed FROM gym_visits WHERE id = 'v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("owner-a", cursor.getString(0))
            assertEquals("2023-11-14", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.query("SELECT ownerUID, status FROM fasting_sessions WHERE id = 'f1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("owner-a", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
        }
        db.query("SELECT ownerUID, marker, sourceRecordId FROM health_observations WHERE id = 'h1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("owner-a", cursor.getString(0))
            assertEquals("weight", cursor.getString(1))
            assertEquals("rec-1", cursor.getString(2))
        }
        db.query(
            "SELECT operationUuid FROM deletion_tombstones WHERE ownerUID = 'owner-a' AND entityId = 'v1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("op-t1", cursor.getString(0))
        }

        // Composite keys: the same entity id is now allowed once per owner.
        db.execSQL(
            "INSERT INTO workout_sessions (ownerUID, id, state, startedUtc, endedUtc, sequence, payloadJson, revision) " +
                "VALUES ('owner-b', 's1', 'COMPLETED', 1700000000000, 1700003600000, 1, '{\"id\":\"s1\"}', 1)"
        )
        db.query("SELECT COUNT(*) FROM workout_sessions WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.execSQL(
            "INSERT INTO deletion_tombstones (ownerUID, entityType, entityId, deletedUtc, operationUuid) " +
                "VALUES ('owner-b', 'gym_visit', 'v1', 1700000002000, 'op-t2')"
        )
        // workout_sets exists post-migration.
        db.execSQL(
            "INSERT INTO workout_sets (ownerUID, id, sessionId, completedUtc, payloadJson, revision) " +
                "VALUES ('owner-a', 'set-1', 's1', NULL, '{}', 1)"
        )
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
