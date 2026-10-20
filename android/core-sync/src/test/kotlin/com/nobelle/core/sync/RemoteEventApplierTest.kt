package com.nobelle.core.sync

import com.nobelle.core.data.FastingScheduleDao
import com.nobelle.core.data.FastingScheduleEntity
import com.nobelle.core.data.FastingSessionDao
import com.nobelle.core.data.FastingSessionEntity
import com.nobelle.core.data.GymVisitDao
import com.nobelle.core.data.GymVisitEntity
import com.nobelle.core.data.HealthObservationDao
import com.nobelle.core.data.HealthObservationEntity
import com.nobelle.core.data.OutboxDao
import com.nobelle.core.data.OutdoorSessionDao
import com.nobelle.core.data.OutdoorSessionEntity
import com.nobelle.core.data.SessionDao
import com.nobelle.core.data.SyncOutboxEntity
import com.nobelle.core.data.TombstoneDao
import com.nobelle.core.data.TombstoneEntity
import com.nobelle.core.data.WatchWorkoutDao
import com.nobelle.core.data.WatchWorkoutEntity
import com.nobelle.core.data.WorkoutSetDao
import com.nobelle.core.data.WorkoutSetEntity
import com.nobelle.core.data.WorkoutSessionEntity
import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.OutdoorSession
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.SyncPaths
import com.nobelle.core.model.WatchSyncCodec
import com.nobelle.core.model.WatchWorkoutPayload
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteEventApplierTest {
    private val owner = "accounts.google.com:subject-a"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun event(
        entityType: String,
        entityId: String,
        op: SyncEvent.Op = SyncEvent.Op.UPSERT,
        operationUuid: String = "op-${entityId}-$op",
        payloadJson: String = "{}",
        namespace: String = owner,
    ) = SyncEvent(
        schemaVersion = 1,
        accountNamespace = namespace,
        installationId = "install-remote",
        entityId = entityId,
        entityType = entityType,
        operationUuid = operationUuid,
        revision = 1,
        op = op,
        payloadJson = payloadJson,
        utc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    @Test
    fun `upsert workout session writes session and sets then marks applied`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val session = WorkoutSession(
            id = "s1",
            ownerUID = owner,
            category = "strength",
            state = WorkoutState.COMPLETED,
            startedUtc = Instant.fromEpochMilliseconds(1_699_999_000_000L),
            endedUtc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            originDeviceId = "install-remote",
            sequence = 4,
            revision = 3,
        )
        val sets = listOf(
            ExerciseSet(id = "set-1", sessionId = "s1", exerciseId = "squat", setIndex = 0,
                completedUtc = Instant.fromEpochMilliseconds(1_699_999_500_000L)),
            ExerciseSet(id = "set-2", sessionId = "s1", exerciseId = "squat", setIndex = 1,
                completedUtc = Instant.fromEpochMilliseconds(1_699_999_900_000L)),
        )
        val ev = event(
            "workout_session", "s1",
            payloadJson = json.encodeToString(WorkoutPayload.serializer(), WorkoutPayload(session, sets)),
        )

        applier.apply(ev)

        val sessionRow = store.sessions.byId(owner, "s1")
        assertNotNull(sessionRow)
        assertEquals("COMPLETED", sessionRow!!.state)
        assertEquals(3L, sessionRow.revision)
        assertEquals(2, store.workoutSets.bySession(owner, "s1").size)
        assertTrue(applier.seenOperation(ev.operationUuid))
        assertEquals("APPLIED", store.outbox.rows[ev.operationUuid]!!.state)
    }

    @Test
    fun `delete gym visit writes tombstone removes row and marks applied`() = runBlocking {
        val store = FakeStore()
        store.gymVisits.upsert(
            GymVisitEntity(
                ownerUID = owner, id = "v1", startInstant = 1L, endInstant = null,
                localAttendanceDate = "2023-11-14", confirmed = true,
                payloadJson = "{}", revision = 1, deletedUtc = null,
            )
        )
        val applier = RemoteEventApplier(store, owner)
        val ev = event("gym_visit", "v1", op = SyncEvent.Op.DELETE)

        applier.apply(ev)

        assertNull(store.gymVisits.byId(owner, "v1"))
        val tombstone = store.tombstones.byEntity(owner, "gym_visit", "v1")
        assertNotNull(tombstone)
        assertEquals(ev.operationUuid, tombstone!!.operationUuid)
        assertEquals(ev.utc.toEpochMilliseconds(), tombstone.deletedUtc)
        assertTrue(applier.seenOperation(ev.operationUuid))
    }

    @Test
    fun `unknown entity type stays unapplied`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val ev = event("future_entity", "x1", operationUuid = "op-future")

        applier.apply(ev)

        assertFalse(applier.seenOperation("op-future"))
        assertNull(store.outbox.rows["op-future"])
        assertNull(store.tombstones.byEntity(owner, "future_entity", "x1"))
    }

    @Test
    fun `existing tombstone blocks later upsert but op still counts applied`() = runBlocking {
        val store = FakeStore()
        store.tombstones.upsert(
            TombstoneEntity(owner, "gym_visit", "v1", deletedUtc = 5L, operationUuid = "op-del")
        )
        val applier = RemoteEventApplier(store, owner)
        val visit = GymVisit(
            id = "v1", ownerUID = owner,
            startInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            originalTimeZone = "America/Toronto",
            localAttendanceDate = "2023-11-14",
        )
        val ev = event(
            "gym_visit", "v1",
            payloadJson = json.encodeToString(GymVisit.serializer(), visit),
        )

        applier.apply(ev)

        assertNull(store.gymVisits.byId(owner, "v1"))
        assertTrue(applier.seenOperation(ev.operationUuid))
    }

    @Test
    fun `own pending row is not seen until applied - reinstall restore path`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val ev = event("gym_visit", "v1", operationUuid = "op-own")
        // Simulate a row this device wrote pre-reinstall: uploaded, never applied here.
        store.outbox.rows["op-own"] = SyncOutboxEntity(
            operationUuid = "op-own", ownerUID = owner, entityId = "v1",
            entityType = "gym_visit", state = "UPLOADED", attempts = 1,
            lastError = null, createdUtc = 1L, payloadJson = "{}",
        )

        assertFalse(applier.seenOperation("op-own"))
        val visit = GymVisit(
            id = "v1", ownerUID = owner,
            startInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            originalTimeZone = "America/Toronto",
            localAttendanceDate = "2023-11-14",
        )
        applier.apply(ev.copy(payloadJson = json.encodeToString(GymVisit.serializer(), visit)))

        assertTrue(applier.seenOperation("op-own"))
        // markApplied flips the existing row in place; attempts are untouched.
        assertEquals(1, store.outbox.rows["op-own"]!!.attempts)
        assertNotNull(store.gymVisits.byId(owner, "v1"))
    }

    @Test
    fun `watch workout upsert lands in watch_workouts`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val payload = WatchWorkoutPayload(
            id = "w1", activity = "run", startedUtc = 100L, endedUtc = 3_700L,
            durationSec = 3_600L, hrSampleCount = 12, avgBpm = 140.5, maxBpm = 171.0,
        )
        val ev = event(
            SyncPaths.ENTITY_TYPE_WATCH_WORKOUT, "w1",
            payloadJson = WatchSyncCodec.encodeWorkoutPayload(payload),
        )

        applier.apply(ev)

        val row = store.watchWorkouts.byId(owner, "w1")
        assertNotNull(row)
        assertEquals("run", row!!.activity)
        assertEquals(140.5, row.avgBpm!!, 0.0001)
        assertEquals(171.0, row.maxBpm!!, 0.0001)
        assertTrue(applier.seenOperation(ev.operationUuid))
    }

    @Test
    fun `outdoor session upsert lands in outdoor_sessions and honors revision`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val session = OutdoorSession(
            id = "o1", ownerUID = owner, activity = "run",
            startedUtc = Instant.fromEpochMilliseconds(1_699_999_000_000L),
            endedUtc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            durationSec = 600, distanceMeters = 1_200.0,
            route = emptyList(), pointCount = 3,
            createdUtc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            revision = 2,
        )
        val ev = event(
            "outdoor_session", "o1",
            payloadJson = json.encodeToString(OutdoorSession.serializer(), session),
        )

        applier.apply(ev)

        val row = store.outdoorSessions.byId(owner, "o1")
        assertNotNull(row)
        assertEquals("run", row!!.activity)
        assertEquals(1_200.0, row.distanceMeters, 0.0001)
        assertEquals(3, row.pointCount)
        assertTrue(applier.seenOperation(ev.operationUuid))

        // A staler revision must not regress the row but still counts applied.
        val stale = ev.copy(
            operationUuid = "op-o1-stale",
            payloadJson = json.encodeToString(
                OutdoorSession.serializer(), session.copy(distanceMeters = 1.0, revision = 1)
            ),
        )
        applier.apply(stale)
        assertEquals(1_200.0, store.outdoorSessions.byId(owner, "o1")!!.distanceMeters, 0.0001)
        assertTrue(applier.seenOperation("op-o1-stale"))

        // DELETE tombstones and removes the row.
        val del = event("outdoor_session", "o1", op = SyncEvent.Op.DELETE)
        applier.apply(del)
        assertNull(store.outdoorSessions.byId(owner, "o1"))
        assertNotNull(store.tombstones.byEntity(owner, "outdoor_session", "o1"))
        assertTrue(applier.seenOperation(del.operationUuid))
    }

    @Test
    fun `malformed payload writes nothing and stays unapplied`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val ev = event("gym_visit", "v1", payloadJson = "not-json{{{")

        applier.apply(ev)

        assertNull(store.gymVisits.byId(owner, "v1"))
        assertFalse(applier.seenOperation(ev.operationUuid))
        assertNull(store.outbox.rows[ev.operationUuid])
    }

    @Test
    fun `foreign namespace event is ignored entirely`() = runBlocking {
        val store = FakeStore()
        val applier = RemoteEventApplier(store, owner)
        val visit = GymVisit(
            id = "v1", ownerUID = "accounts.google.com:subject-b",
            startInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            originalTimeZone = "America/Toronto",
            localAttendanceDate = "2023-11-14",
        )
        val ev = event(
            "gym_visit", "v1", namespace = "accounts.google.com:subject-b",
            payloadJson = json.encodeToString(GymVisit.serializer(), visit),
        )

        applier.apply(ev)

        assertNull(store.gymVisits.byId(owner, "v1"))
        assertNull(store.outbox.rows[ev.operationUuid])
    }

    // In-memory fakes: same owner-scoped semantics as the Room DAOs, no Android.

    private class FakeOutboxDao : OutboxDao {
        val rows = linkedMapOf<String, SyncOutboxEntity>()
        override suspend fun enqueue(op: SyncOutboxEntity): Long =
            if (rows.putIfAbsent(op.operationUuid, op) == null) 1L else -1L
        override suspend fun pending(ownerUID: String, limit: Int) =
            rows.values.filter { it.ownerUID == ownerUID && it.state == "PENDING" }
                .sortedBy { it.createdUtc }.take(limit)
        override suspend fun byUuid(ownerUID: String, operationUuid: String) =
            rows[operationUuid]?.takeIf { it.ownerUID == ownerUID }
        override suspend fun appliedExists(ownerUID: String, operationUuid: String) =
            rows[operationUuid]?.let { it.ownerUID == ownerUID && it.state == "APPLIED" } == true
        override suspend fun markState(ownerUID: String, id: String, state: String, error: String?): Int {
            val row = rows[id]?.takeIf { it.ownerUID == ownerUID } ?: return 0
            rows[id] = row.copy(state = state, attempts = row.attempts + 1, lastError = error)
            return 1
        }
        override suspend fun markApplied(ownerUID: String, operationUuid: String): Int {
            val row = rows[operationUuid]?.takeIf { it.ownerUID == ownerUID } ?: return 0
            rows[operationUuid] = row.copy(state = "APPLIED")
            return 1
        }
    }

    private class FakeSessionDao : SessionDao {
        val rows = linkedMapOf<Pair<String, String>, WorkoutSessionEntity>()
        override suspend fun upsert(s: WorkoutSessionEntity) { rows[s.ownerUID to s.id] = s }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun activeSession(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID && it.state in setOf("ACTIVE", "PAUSED") }
            .maxByOrNull { it.startedUtc ?: 0L }
        override suspend fun list(ownerUID: String) =
            rows.values.filter { it.ownerUID == ownerUID }.sortedByDescending { it.startedUtc }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeWorkoutSetDao : WorkoutSetDao {
        val rows = linkedMapOf<Pair<String, String>, WorkoutSetEntity>()
        override suspend fun upsert(set: WorkoutSetEntity) { rows[set.ownerUID to set.id] = set }
        override suspend fun bySession(ownerUID: String, sessionId: String) =
            rows.values.filter { it.ownerUID == ownerUID && it.sessionId == sessionId }
        override suspend fun deleteBySession(ownerUID: String, sessionId: String) {
            rows.entries.removeAll { it.key.first == ownerUID && it.value.sessionId == sessionId }
        }
    }

    private class FakeGymVisitDao : GymVisitDao {
        val rows = linkedMapOf<Pair<String, String>, GymVisitEntity>()
        override suspend fun upsert(v: GymVisitEntity) { rows[v.ownerUID to v.id] = v }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun list(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID && it.deletedUtc == null }
            .sortedByDescending { it.startInstant }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeFastingSessionDao : FastingSessionDao {
        val rows = linkedMapOf<Pair<String, String>, FastingSessionEntity>()
        override suspend fun upsert(s: FastingSessionEntity) { rows[s.ownerUID to s.id] = s }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun active(ownerUID: String) = rows.values
            .firstOrNull { it.ownerUID == ownerUID && it.status == "ACTIVE" && it.deletedUtc == null }
        override suspend fun completedByWindow(ownerUID: String, startMs: Long, endMs: Long) =
            rows.values.filter {
                it.ownerUID == ownerUID && it.actualStartInstant == startMs &&
                    it.actualEndInstant == endMs && it.status == "COMPLETED" && it.deletedUtc == null
            }.maxByOrNull { it.revision }
        override suspend fun list(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID && it.deletedUtc == null }
            .sortedByDescending { it.actualStartInstant }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeFastingScheduleDao : FastingScheduleDao {
        val rows = linkedMapOf<Pair<String, String>, FastingScheduleEntity>()
        override suspend fun upsert(s: FastingScheduleEntity) { rows[s.ownerUID to s.id] = s }
        override suspend fun enabled(ownerUID: String) =
            rows.values.firstOrNull { it.ownerUID == ownerUID && it.enabled }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun list(ownerUID: String) =
            rows.values.filter { it.ownerUID == ownerUID }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeHealthObservationDao : HealthObservationDao {
        val rows = linkedMapOf<Pair<String, String>, HealthObservationEntity>()
        override suspend fun upsert(o: HealthObservationEntity) { rows[o.ownerUID to o.id] = o }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun list(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID && it.deletedUtc == null }
            .sortedByDescending { it.measuredUtc }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeWatchWorkoutDao : WatchWorkoutDao {
        val rows = linkedMapOf<Pair<String, String>, WatchWorkoutEntity>()
        override suspend fun upsert(w: WatchWorkoutEntity) { rows[w.ownerUID to w.id] = w }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun list(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID }.sortedByDescending { it.startedUtc }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeOutdoorSessionDao : OutdoorSessionDao {
        val rows = linkedMapOf<Pair<String, String>, OutdoorSessionEntity>()
        override suspend fun upsert(s: OutdoorSessionEntity) { rows[s.ownerUID to s.id] = s }
        override suspend fun byId(ownerUID: String, id: String) = rows[ownerUID to id]
        override suspend fun list(ownerUID: String) = rows.values
            .filter { it.ownerUID == ownerUID && it.deletedUtc == null }
            .sortedByDescending { it.startedUtc }
        override suspend fun hardDelete(ownerUID: String, id: String) { rows.remove(ownerUID to id) }
    }

    private class FakeTombstoneDao : TombstoneDao {
        val rows = linkedMapOf<Triple<String, String, String>, TombstoneEntity>()
        override suspend fun upsert(t: TombstoneEntity) {
            rows[Triple(t.ownerUID, t.entityType, t.entityId)] = t
        }
        override suspend fun byEntity(ownerUID: String, entityType: String, entityId: String) =
            rows[Triple(ownerUID, entityType, entityId)]
    }

    private class FakeStore : RemoteApplyStore {
        val outbox = FakeOutboxDao()
        val sessions = FakeSessionDao()
        val workoutSets = FakeWorkoutSetDao()
        val gymVisits = FakeGymVisitDao()
        val fastingSessions = FakeFastingSessionDao()
        val fastingSchedules = FakeFastingScheduleDao()
        val healthObservations = FakeHealthObservationDao()
        val watchWorkouts = FakeWatchWorkoutDao()
        val outdoorSessions = FakeOutdoorSessionDao()
        val tombstones = FakeTombstoneDao()

        override fun outbox() = outbox
        override fun sessions() = sessions
        override fun workoutSets() = workoutSets
        override fun gymVisits() = gymVisits
        override fun fastingSessions() = fastingSessions
        override fun fastingSchedules() = fastingSchedules
        override fun healthObservations() = healthObservations
        override fun watchWorkouts() = watchWorkouts
        override fun outdoorSessions() = outdoorSessions
        override fun tombstones() = tombstones
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
}
