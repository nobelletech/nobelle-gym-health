package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.data.repository.WorkoutRepository
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.WorkoutState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
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
class WorkoutRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: WorkoutRepository

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
        repo = WorkoutRepository(db, "install-test", now = ::now, newId = ::newId)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun activeSessionIsInvisibleToAnotherOwner() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)

        assertEquals(session.id, repo.active(ownerA)?.id)
        assertNull(repo.active(ownerB))
        assertNull(db.sessions().activeSession(ownerB.uid))
        assertTrue(db.sessions().list(ownerB.uid).isEmpty())
    }

    @Test
    fun pendingOutboxIsOwnerScoped() = runBlocking {
        db.outbox().enqueue(
            SyncOutboxEntity(
                operationUuid = "op-b",
                ownerUID = ownerB.uid,
                entityId = "entity-b",
                entityType = "gym_visit",
                state = "PENDING",
                attempts = 0,
                lastError = null,
                createdUtc = 1L,
                payloadJson = "{}",
            )
        )
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        repo.finish(ownerA, session.id)

        val pendingA = db.outbox().pending(ownerA.uid)
        assertTrue(pendingA.isNotEmpty())
        assertTrue(pendingA.all { it.ownerUID == ownerA.uid })
        assertFalse(pendingA.any { it.operationUuid == "op-b" })
        assertEquals(listOf("op-b"), db.outbox().pending(ownerB.uid).map { it.operationUuid })
    }

    @Test
    fun activeSessionAndSetsSurviveRepositoryRecreation() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        repo.addSet(
            ownerA, session.id, exerciseId = "squat", reps = 5, loadKg = 80.0,
            durationSec = null, restSec = 120, rpe = 8.0, rir = 2.0, tempo = null,
            painFlag = false, notes = null,
        )

        val recreated = WorkoutRepository(db, "install-test", now = ::now, newId = ::newId)
        val active = recreated.active(ownerA)
        assertNotNull(active)
        assertEquals(session.id, active!!.id)
        assertEquals(WorkoutState.ACTIVE, active.state)
        val sets = recreated.sets(ownerA, session.id)
        assertEquals(1, sets.size)
        assertEquals("squat", sets[0].exerciseId)
        assertEquals(0, sets[0].setIndex)
        assertEquals(5, sets[0].reps)
    }

    @Test
    fun pauseResumeFinishTransitionsIncrementSequenceAndRevision() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        assertEquals(1L, session.sequence)
        assertEquals(1L, session.revision)

        val paused = repo.pause(ownerA, session.id)
        assertEquals(WorkoutState.PAUSED, paused.state)
        assertEquals(2L, paused.sequence)
        assertEquals(2L, paused.revision)

        val resumed = repo.resume(ownerA, session.id)
        assertEquals(WorkoutState.ACTIVE, resumed.state)
        assertEquals(3L, resumed.sequence)
        assertEquals(4L, repo.finish(ownerA, session.id).sequence)
        assertNull(repo.active(ownerA)) // completed sessions are not active
        val finished = db.sessions().byId(ownerA.uid, session.id)!!
        assertEquals(WorkoutState.COMPLETED.name, finished.state)
        assertNotNull(finished.endedUtc)
        assertEquals(4L, finished.revision)

        try {
            repo.resume(ownerA, session.id)
            fail("resuming a completed workout must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            repo.pause(ownerA, session.id)
            fail("pausing a completed workout must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun repeatedFinishIsRejectedAndExactlyOneOutboxOperationExists() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        repo.addSet(
            ownerA, session.id, exerciseId = "squat", reps = 5, loadKg = 80.0,
            durationSec = null, restSec = null, rpe = null, rir = null, tempo = null,
            painFlag = false, notes = null,
        )
        val finished = repo.finish(ownerA, session.id)

        try {
            repo.finish(ownerA, session.id)
            fail("finishing twice must be rejected")
        } catch (expected: IllegalArgumentException) {
        }

        val ops = db.outbox().pending(ownerA.uid).filter { it.entityId == session.id }
        assertEquals(1, ops.size)
        val event = json.decodeFromString(SyncEvent.serializer(), ops[0].payloadJson)
        assertEquals(SyncEvent.Op.UPSERT, event.op)
        assertEquals("workout_session", event.entityType)
        assertEquals(session.id, event.entityId)
        assertEquals(ownerA.uid, event.accountNamespace)
        assertEquals(finished.revision, event.revision)
        assertEquals("install-test", event.installationId)
        val payload = json.decodeFromString(WorkoutPayload.serializer(), event.payloadJson)
        assertEquals(session.id, payload.session.id)
        assertEquals(WorkoutState.COMPLETED, payload.session.state)
        assertEquals(1, payload.sets.size)
    }

    @Test
    fun startIsRejectedWhileAnotherActiveExistsForSameOwnerOnly() = runBlocking {
        repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        try {
            repo.start(ownerA, "cardio", "device-x", "America/Toronto", -240)
            fail("a second active workout for the same owner must be rejected")
        } catch (expected: IllegalStateException) {
        }
        val forOtherOwner = repo.start(ownerB, "cardio", "device-y", "America/Toronto", -240)
        assertNotNull(forOtherOwner) // isolation, not blocking
    }

    @Test
    fun addSetValidatesMeasurementsAndIndexesSequentially() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        try {
            repo.addSet(
                ownerA, session.id, exerciseId = "squat", reps = -1, loadKg = null,
                durationSec = null, restSec = null, rpe = null, rir = null, tempo = null,
                painFlag = false, notes = null,
            )
            fail("negative reps must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        val first = repo.addSet(
            ownerA, session.id, exerciseId = "squat", reps = 5, loadKg = null,
            durationSec = null, restSec = null, rpe = null, rir = null, tempo = null,
            painFlag = false, notes = null,
        )
        val second = repo.addSet(
            ownerA, session.id, exerciseId = "squat", reps = 5, loadKg = null,
            durationSec = null, restSec = null, rpe = null, rir = null, tempo = null,
            painFlag = false, notes = null,
        )
        assertEquals(0, first.setIndex)
        assertEquals(1, second.setIndex)
    }

    @Test
    fun discardIsLocalOnlyAndNeverEnqueuesSync() = runBlocking {
        val session = repo.start(ownerA, "strength", "device-x", "America/Toronto", -240)
        val discarded = repo.discard(ownerA, session.id)
        assertEquals(WorkoutState.DISCARDED, discarded.state)
        assertTrue(db.outbox().pending(ownerA.uid).isEmpty())
    }
}
