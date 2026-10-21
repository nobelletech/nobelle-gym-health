package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.WorkoutTemplateRepository
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.model.TemplateExercise
import com.nobelle.core.model.WorkoutTemplate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutTemplateRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: WorkoutTemplateRepository
    private val owner = OwnerId("https://accounts.google.com", "subject-a")
    private val other = OwnerId("https://accounts.google.com", "subject-b")

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
        repo = WorkoutTemplateRepository(db, "install-test")
    }
    @After fun tearDown() = db.close()

    @Test fun upsertOutboxRevisionAndDeleteAreOwnerScoped() = runBlocking {
        val template = WorkoutTemplate("t1", owner.uid, "Upper", listOf(TemplateExercise("push-up", 3, reps = 8)), "Strength")
        val saved = repo.upsert(owner, template)
        assertEquals(saved, repo.get(owner, "t1"))
        assertNull(repo.get(other, "t1"))
        val op = db.outbox().pending(owner.uid).single()
        val event = Json.decodeFromString(SyncEvent.serializer(), op.payloadJson)
        assertEquals("workout_template", event.entityType)
        assertEquals(SyncEvent.Op.UPSERT, event.op)
        assertEquals(owner.uid, event.accountNamespace)
        assertEquals(saved, Json.decodeFromString(WorkoutTemplate.serializer(), event.payloadJson))
        assertEquals(2L, repo.upsert(owner, saved.copy(name = "Edited")).revision)
        repo.delete(owner, "t1")
        assertNull(repo.get(owner, "t1"))
        assertNotNull(db.tombstones().byEntity(owner.uid, "workout_template", "t1"))
        assertEquals(3, db.outbox().pending(owner.uid).size)
    }

    @Test fun rejectsOwnerMismatchWithoutWriting() = runBlocking {
        val template = WorkoutTemplate("t1", other.uid, "Upper", listOf(TemplateExercise("push-up", 3, reps = 8)), "Strength")
        try {
            repo.upsert(owner, template)
            fail("owner mismatch must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repo.list(owner).isEmpty())
        assertTrue(db.outbox().pending(owner.uid).isEmpty())
    }
}
