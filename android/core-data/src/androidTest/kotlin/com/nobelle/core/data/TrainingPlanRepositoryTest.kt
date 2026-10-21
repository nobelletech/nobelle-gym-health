package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.TrainingPlanRepository
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.TrainingPlan
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingPlanRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: TrainingPlanRepository
    private val owner = OwnerId("https://accounts.google.com", "subject-a")
    private val other = OwnerId("https://accounts.google.com", "subject-b")

    private fun plan(id: String, ownerUID: String = owner.uid, weekStart: String = "2024-01-01") =
        TrainingPlan(
            id = id, ownerUID = ownerUID, weekStartDate = weekStart, generatedBy = "rules-v1",
            plannedSessions = listOf(
                PlannedSession(id = "ps1", ownerUID = ownerUID, localDate = weekStart,
                    templateId = null, category = "full_body"),
                PlannedSession(id = "ps2", ownerUID = ownerUID, localDate = "2024-01-03",
                    templateId = null, category = "mobility"),
            ),
        )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
        repo = TrainingPlanRepository(db, "install-test")
    }
    @After fun tearDown() = db.close()

    @Test fun upsertOutboxRevisionAndDeleteAreOwnerScoped() = runBlocking {
        val saved = repo.upsert(owner, plan("p1"))
        assertEquals(saved, repo.get(owner, "p1"))
        assertNull(repo.get(other, "p1"))
        val op = db.outbox().pending(owner.uid).single()
        val event = Json.decodeFromString(SyncEvent.serializer(), op.payloadJson)
        assertEquals("training_plan", event.entityType)
        assertEquals(SyncEvent.Op.UPSERT, event.op)
        assertEquals(owner.uid, event.accountNamespace)
        assertEquals(saved, Json.decodeFromString(TrainingPlan.serializer(), event.payloadJson))
        assertEquals(2L, repo.upsert(owner, saved.copy(generatedBy = "rules-v1+user-edit")).revision)
        repo.delete(owner, "p1")
        assertNull(repo.get(owner, "p1"))
        assertNotNull(db.tombstones().byEntity(owner.uid, "training_plan", "p1"))
        assertEquals(3, db.outbox().pending(owner.uid).size)
    }

    @Test fun statusUpdateBumpsRevisionAndEnqueuesOneEvent() = runBlocking {
        val saved = repo.upsert(owner, plan("p1"))
        val updated = repo.setPlannedSessionStatus(
            owner, "p1", "ps2", PlannedSession.Status.SKIPPED)
        assertEquals(saved.revision + 1, updated.revision)
        assertEquals(PlannedSession.Status.SKIPPED,
            updated.plannedSessions.single { it.id == "ps2" }.status)
        assertEquals(PlannedSession.Status.PLANNED,
            updated.plannedSessions.single { it.id == "ps1" }.status)
        // One plan entity event per edit; nothing else was queued.
        assertEquals(2, db.outbox().pending(owner.uid).size)
        try {
            repo.setPlannedSessionStatus(owner, "p1", "missing", PlannedSession.Status.SKIPPED)
            fail("unknown session must fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun validationRejectsNonMondayForeignSessionsAndOwnerMismatch() = runBlocking {
        try {
            repo.upsert(owner, plan("p1", weekStart = "2024-01-02")) // Tuesday
            fail("non-Monday week start must fail")
        } catch (_: IllegalArgumentException) { }
        try {
            repo.upsert(owner, plan("p1", ownerUID = other.uid))
            fail("owner mismatch must fail")
        } catch (_: IllegalArgumentException) { }
        val foreignSession = plan("p1").copy(
            plannedSessions = listOf(
                PlannedSession(id = "ps1", ownerUID = other.uid, localDate = "2024-01-01",
                    templateId = null, category = "full_body"),
            ),
        )
        try {
            repo.upsert(owner, foreignSession)
            fail("foreign planned session must fail")
        } catch (_: IllegalArgumentException) { }
        val outOfWeek = plan("p1").copy(
            plannedSessions = listOf(
                PlannedSession(id = "ps1", ownerUID = owner.uid, localDate = "2024-01-09",
                    templateId = null, category = "full_body"),
            ),
        )
        try {
            repo.upsert(owner, outOfWeek)
            fail("session outside the week must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repo.list(owner).isEmpty())
        assertTrue(db.outbox().pending(owner.uid).isEmpty())
    }
}
