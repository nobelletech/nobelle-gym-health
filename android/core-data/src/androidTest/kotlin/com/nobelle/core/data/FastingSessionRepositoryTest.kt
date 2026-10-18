package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.FastingSessionRepository
import com.nobelle.core.model.FastingSession
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastingSessionRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: FastingSessionRepository

    private var tick = 0L
    private var idCounter = 0
    private fun now() = Instant.fromEpochMilliseconds(1_700_000_000_000L + (tick++) * 1000)
    private fun newId() = "generated-${idCounter++}"

    private val ownerA = OwnerId("https://accounts.google.com", "subject-a")
    private val ownerB = OwnerId("https://accounts.google.com", "subject-b")
    private val toronto = TimeZone.of("America/Toronto")
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
        repo = FastingSessionRepository(db, "install-test", now = ::now, newId = ::newId)
    }

    @After
    fun tearDown() = db.close()

    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime(2024, 5, 1, hour, minute).toInstant(toronto)

    @Test
    fun startRejectsSecondActiveForSameOwnerOnly() = runBlocking {
        repo.start(ownerA, at(8), toronto.id, 16L * 3600, null)

        try {
            repo.start(ownerA, at(9), toronto.id, 16L * 3600, null)
            fail("second active fast for the same owner must be rejected")
        } catch (expected: IllegalArgumentException) {
        }

        val forB = repo.start(ownerB, at(9), toronto.id, 16L * 3600, null)
        assertEquals(FastingSession.Status.ACTIVE, forB.status)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(1, repo.list(ownerB).size)
        assertNull(repo.get(ownerA, forB.id))
    }

    @Test
    fun startRejectsNonPositiveTarget() = runBlocking {
        try {
            repo.start(ownerA, at(8), toronto.id, 0L, null)
            fail("zero target must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(repo.list(ownerA).isEmpty())
    }

    @Test
    fun endRejectsBeforeStartThenCompletes() = runBlocking {
        val session = repo.start(ownerA, at(8), toronto.id, 8L * 3600, "sched-1")

        try {
            repo.end(ownerA, session.id, at(7), toronto.id)
            fail("end before start must be rejected")
        } catch (expected: IllegalArgumentException) {
        }

        val ended = repo.end(ownerA, session.id, at(20), toronto.id)
        assertEquals(FastingSession.Status.COMPLETED, ended.status)
        assertEquals(at(20), ended.actualEndInstant)
        assertEquals(toronto.id, ended.originalEndZone)
        assertFalse(ended.endedEarly) // 12h elapsed meets the 8h target
        assertEquals(2L, ended.revision)
        assertNull(repo.active(ownerA))
    }

    @Test
    fun cancelMarksCancelledNotDeleted() = runBlocking {
        val session = repo.start(ownerA, at(8), toronto.id, 16L * 3600, null)
        val cancelled = repo.cancel(ownerA, session.id, at(10), toronto.id)

        assertEquals(FastingSession.Status.CANCELLED, cancelled.status)
        assertEquals(at(10), cancelled.actualEndInstant)
        assertTrue(cancelled.endedEarly)
        assertNull(cancelled.deletedUtc)
        assertTrue(repo.list(ownerA).single().id == session.id)
    }

    @Test
    fun createCompletedBackdatesAndComputesEndedEarly() = runBlocking {
        val created = repo.createCompleted(
            ownerA, at(8), at(20, 30),
            toronto.id, toronto.id, 14L * 3600, "sched-1", "  forgot to log  ",
        )
        assertEquals(FastingSession.Status.COMPLETED, created.status)
        assertEquals("forgot to log", created.notes)
        assertEquals(1L, created.revision)
        // 12.5h elapsed < 14h target
        assertTrue(created.endedEarly)

        try {
            repo.createCompleted(ownerA, at(9), at(8), toronto.id, toronto.id, 3600, null, null)
            fail("end before start must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun createCompletedIsIdempotentForTheExactWindow() = runBlocking {
        val end8 = Instant.fromEpochMilliseconds(at(8).toEpochMilliseconds() + 86_400_000)
        val first = repo.createCompleted(
            ownerA, at(20), end8,
            toronto.id, toronto.id, 12L * 3600, "sched-1", "first note",
        )
        // Identical actual interval, different notes — the stored record wins.
        val second = repo.createCompleted(
            ownerA, at(20), end8,
            toronto.id, toronto.id, 12L * 3600, null, "second note",
        )

        assertEquals(first.id, second.id)
        assertEquals("first note", second.notes)
        assertEquals(1L, second.revision)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(
            1,
            db.outbox().pending(ownerA.uid)
                .count { it.entityType == "fasting_session" && it.entityId == first.id },
        )

        // A different window is a different fast.
        val distinct = repo.createCompleted(
            ownerA, at(20), Instant.fromEpochMilliseconds(end8.toEpochMilliseconds() + 3_600_000),
            toronto.id, toronto.id, 12L * 3600, null, null,
        )
        assertTrue(distinct.id != first.id)
        assertEquals(2, repo.list(ownerA).size)
    }

    @Test
    fun elapsedDerivesFromRealInstantsAcrossDst() = runBlocking {
        // Toronto springs forward 02:00 -> 03:00 on 2024-03-10: the wall clock
        // shows 01:30 -> 03:30 but only one real hour elapsed.
        val start = LocalDateTime(2024, 3, 10, 1, 30).toInstant(toronto)
        val end = LocalDateTime(2024, 3, 10, 3, 30).toInstant(toronto)
        val session = repo.createCompleted(
            ownerA, start, end, toronto.id, toronto.id, 2L * 3600, null, null,
        )
        assertEquals(3600L, session.elapsedAt(end).inWholeSeconds)
        assertTrue(session.endedEarly) // 1 real hour < 2h target — metadata only
    }

    @Test
    fun activeFastSurvivesRepositoryRecreation() = runBlocking {
        val session = repo.start(ownerA, at(8), toronto.id, 16L * 3600, "sched-1")
        // A new repository instance on the same durable DB reads the same
        // state — recovery is data-backed, not in-memory.
        val repo2 = FastingSessionRepository(db, "install-test")
        assertEquals(session.id, repo2.active(ownerA)?.id)
        assertNull(repo2.active(ownerB))
    }

    @Test
    fun deleteWritesTombstoneAndDeleteEvent() = runBlocking {
        val session = repo.createCompleted(
            ownerA, at(8), at(20), toronto.id, toronto.id, 12L * 3600, null, null,
        )
        repo.delete(ownerA, session.id)

        val tombstone = db.tombstones().byEntity(ownerA.uid, "fasting_session", session.id)
        assertNotNull(tombstone)
        val deleteOp = db.outbox().pending(ownerA.uid)
            .filter { it.entityId == session.id }
            .map { it.operationUuid to json.decodeFromString(SyncEvent.serializer(), it.payloadJson) }
            .single { it.second.op == SyncEvent.Op.DELETE }
        assertEquals(tombstone!!.operationUuid, deleteOp.first)
        assertTrue(repo.list(ownerA).isEmpty())
    }
}
