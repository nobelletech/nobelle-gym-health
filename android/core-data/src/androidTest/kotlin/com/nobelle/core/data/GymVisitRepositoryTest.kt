package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.GymVisitRepository
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymVisitRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: GymVisitRepository

    private var tick = 0L
    private var idCounter = 0
    private fun now() = Instant.fromEpochMilliseconds(1_700_000_000_000L + (tick++) * 1000)
    private fun newId() = "generated-${idCounter++}"

    private val ownerA = OwnerId("https://accounts.google.com", "subject-a")
    private val ownerB = OwnerId("https://accounts.google.com", "subject-b")
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
        repo = GymVisitRepository(db, "install-test", now = ::now, newId = ::newId)
    }

    @After
    fun tearDown() = db.close()

    private fun visit(id: String, owner: OwnerId) = GymVisit(
        id = id,
        ownerUID = owner.uid,
        startInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        endInstant = Instant.fromEpochMilliseconds(1_700_003_600_000L),
        originalTimeZone = "America/Toronto",
        localAttendanceDate = "2023-11-14",
    )

    @Test
    fun deleteCreatesTombstoneAndDeleteEventSharingOneOperationUuid() = runBlocking {
        repo.upsert(ownerA, visit("visit-a1", ownerA))

        val deleted = repo.delete(ownerA, "visit-a1")
        assertNotNull(deleted.deletedUtc)
        assertEquals(2L, deleted.revision)

        val tombstone = db.tombstones().byEntity(ownerA.uid, "gym_visit", "visit-a1")
        assertNotNull(tombstone)
        assertEquals("visit-a1", tombstone!!.entityId)
        assertEquals(ownerA.uid, tombstone.ownerUID)

        val events = db.outbox().pending(ownerA.uid)
            .filter { it.entityId == "visit-a1" }
            .map { it.operationUuid to json.decodeFromString(SyncEvent.serializer(), it.payloadJson) }
        val deleteOp = events.single { it.second.op == SyncEvent.Op.DELETE }
        assertEquals(tombstone.operationUuid, deleteOp.first)
        assertEquals(tombstone.operationUuid, deleteOp.second.operationUuid)
        assertEquals(deleted.revision, deleteOp.second.revision)
        assertTrue(db.gymVisits().list(ownerA.uid).isEmpty())
        assertNotNull(repo.get(ownerA, "visit-a1")?.deletedUtc)
    }

    @Test
    fun recordsAreIsolatedBetweenOwners() = runBlocking {
        repo.upsert(ownerA, visit("visit-a1", ownerA))
        repo.upsert(ownerB, visit("visit-b1", ownerB))

        assertEquals(listOf("visit-a1"), repo.list(ownerA).map { it.id })
        assertEquals(listOf("visit-b1"), repo.list(ownerB).map { it.id })
        assertNull(repo.get(ownerA, "visit-b1"))
        repo.delete(ownerA, "visit-a1")
        assertNull(db.tombstones().byEntity(ownerB.uid, "gym_visit", "visit-a1"))
    }

    @Test
    fun sameIdUpsertsForBothOwnersReplaceNeither() = runBlocking {
        repo.upsert(ownerA, visit("shared-id", ownerA).copy(notes = "belongs to A"))
        repo.upsert(ownerB, visit("shared-id", ownerB).copy(notes = "belongs to B", revision = 9L))

        val a = repo.get(ownerA, "shared-id")!!
        val b = repo.get(ownerB, "shared-id")!!
        assertEquals("belongs to A", a.notes)
        assertEquals("belongs to B", b.notes)
        assertEquals(9L, b.revision)
        assertEquals(ownerA.uid, a.ownerUID)
        assertEquals(ownerB.uid, b.ownerUID)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(1, repo.list(ownerB).size)
    }

    @Test
    fun upsertEnqueuesOwnerScopedUpsertEvent() = runBlocking {
        val visit = visit("visit-a1", ownerA)
        repo.upsert(ownerA, visit)

        val ops = db.outbox().pending(ownerA.uid)
        assertEquals(1, ops.size)
        val event = json.decodeFromString(SyncEvent.serializer(), ops[0].payloadJson)
        assertEquals(SyncEvent.Op.UPSERT, event.op)
        assertEquals("gym_visit", event.entityType)
        assertEquals(ownerA.uid, event.accountNamespace)
        val stored = json.decodeFromString(GymVisit.serializer(), event.payloadJson)
        assertEquals("2023-11-14", stored.localAttendanceDate) // attendance date preserved verbatim
        assertEquals(visit.startInstant, stored.startInstant)
    }

    @Test
    fun createDerivesAttendanceDateFromStartLocalDateEvenOvernight() = runBlocking {
        // 23:30 in Toronto on Jan 5 is Jan 6 in UTC — the attendance day is
        // still Jan 5 because the start's local date governs.
        val start = LocalDateTime(2024, 1, 5, 23, 30)
            .toInstant(TimeZone.of("America/Toronto"))

        val visit = repo.create(ownerA, start, "America/Toronto", gymLabel = "Downtown")

        assertEquals("2024-01-05", visit.localAttendanceDate)
        assertEquals(start, visit.startInstant)
        assertEquals("America/Toronto", visit.originalTimeZone)
        assertEquals("Downtown", visit.gymLabel)
        assertNull(visit.endInstant)
        assertTrue(visit.confirmed)
        assertEquals(1L, visit.revision)
        assertEquals(1, db.outbox().pending(ownerA.uid).size)
        assertTrue(repo.list(ownerA).single().id == visit.id)
    }

    @Test
    fun twoVisitsOnOneLocalDayAreTwoRecordsButOneAttendanceDay() = runBlocking {
        val tz = TimeZone.of("America/Toronto")
        repo.create(ownerA, LocalDateTime(2024, 2, 10, 7, 0).toInstant(tz), tz.id)
        repo.create(ownerA, LocalDateTime(2024, 2, 10, 18, 30).toInstant(tz), tz.id)

        val visits = repo.list(ownerA)
        assertEquals(2, visits.size)
        assertEquals(1, visits.map { it.localAttendanceDate }.distinct().size)
        assertEquals("2024-02-10", visits.first().localAttendanceDate)
    }

    @Test
    fun endRejectsEndBeforeStartAndThenSetsEnd() = runBlocking {
        val tz = TimeZone.of("America/Toronto")
        val start = LocalDateTime(2024, 3, 1, 9, 0).toInstant(tz)
        val visit = repo.create(ownerA, start, tz.id)

        try {
            repo.end(ownerA, visit.id, Instant.fromEpochMilliseconds(start.toEpochMilliseconds() - 300_000))
            fail("end before start must be rejected")
        } catch (expected: IllegalArgumentException) {
            // rejected before any write
        }

        val end = LocalDateTime(2024, 3, 1, 10, 15).toInstant(tz)
        val ended = repo.end(ownerA, visit.id, end)
        assertEquals(end, ended.endInstant)
        assertEquals(start, ended.startInstant)
        assertEquals("2024-03-01", ended.localAttendanceDate)
        assertEquals(2L, ended.revision)
    }

    @Test
    fun editRecomputesAttendanceDateAndKeepsOnlyValidEnd() = runBlocking {
        val tz = TimeZone.of("America/Toronto")
        val start = LocalDateTime(2024, 4, 2, 20, 0).toInstant(tz)
        val visit = repo.create(ownerA, start, tz.id)
        val end = LocalDateTime(2024, 4, 2, 21, 0).toInstant(tz)
        repo.end(ownerA, visit.id, end)

        // Editing the start past its end clears the now-invalid end.
        val laterStart = LocalDateTime(2024, 4, 3, 22, 0).toInstant(tz)
        val edited = repo.edit(ownerA, visit.id, laterStart, tz.id, "Uptown", "Sauna", "note")
        assertEquals("2024-04-03", edited.localAttendanceDate)
        assertEquals(laterStart, edited.startInstant)
        assertNull(edited.endInstant)
        assertEquals("Uptown", edited.gymLabel)
        assertEquals("Sauna", edited.category)
        assertEquals("note", edited.notes)
        assertEquals(3L, edited.revision)

        // Editing within the same day keeps an end that is still after start.
        val earlierStart = LocalDateTime(2024, 4, 3, 20, 0).toInstant(tz)
        val reEdited = repo.edit(ownerA, visit.id, earlierStart, tz.id, null, null, null)
        assertEquals("2024-04-03", reEdited.localAttendanceDate)
        assertNull(reEdited.endInstant) // end stayed cleared from previous edit
        assertEquals(4L, reEdited.revision)
    }

    @Test
    fun linkingWorkoutsIsIdempotentAndQueuesOnlyRealMutations() = runBlocking {
        val tz = TimeZone.of("America/Toronto")
        val visit = repo.create(ownerA, LocalDateTime(2024, 5, 5, 12, 0).toInstant(tz), tz.id)

        val once = repo.linkWorkout(ownerA, visit.id, "workout-1")
        val twice = repo.linkWorkout(ownerA, visit.id, "workout-2")
        val duplicate = repo.linkWorkout(ownerA, visit.id, " workout-1 ")
        val blank = try {
            repo.linkWorkout(ownerA, visit.id, "   ")
            fail("blank workout id must be rejected")
        } catch (expected: IllegalArgumentException) {
            null
        }
        assertNull(blank)

        assertEquals(listOf("workout-1", "workout-2"), twice.workoutIds)
        assertEquals(listOf("workout-1", "workout-2"), duplicate.workoutIds)
        assertEquals(3L, twice.revision)
        assertEquals(3L, duplicate.revision) // duplicate link bumps nothing

        // create + two link mutations = three queued UPSERT events.
        val ops = db.outbox().pending(ownerA.uid)
        assertEquals(3, ops.size)
    }
}
