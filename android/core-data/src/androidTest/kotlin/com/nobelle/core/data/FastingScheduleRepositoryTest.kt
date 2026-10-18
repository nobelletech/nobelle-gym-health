package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.FastingScheduleRepository
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.SyncEvent
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
class FastingScheduleRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: FastingScheduleRepository

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
        repo = FastingScheduleRepository(db, "install-test", now = ::now, newId = ::newId)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun enableCreatesTemplateScheduleAndQueuesUpsert() = runBlocking {
        val schedule = repo.enable(ownerA, 16, "20:00")

        assertTrue(schedule.enabled)
        assertEquals(16, schedule.fastingHours)
        assertEquals(8, schedule.eatingHours)
        assertEquals("20:00", schedule.startLocalTime)
        assertEquals((1..7).toList(), schedule.daysOfWeek)
        assertEquals("FOLLOW_DEVICE", schedule.timeZonePolicy)
        assertEquals(1L, schedule.revision)
        assertEquals(schedule.id, repo.getEnabled(ownerA)?.id)
        assertNull(repo.getEnabled(ownerB))

        val ops = db.outbox().pending(ownerA.uid)
        assertEquals(1, ops.size)
        val event = json.decodeFromString(SyncEvent.serializer(), ops[0].payloadJson)
        assertEquals(SyncEvent.Op.UPSERT, event.op)
        assertEquals("fasting_schedule", event.entityType)
        assertEquals(ownerA.uid, event.accountNamespace)
    }

    @Test
    fun enableRejectsUnknownTemplateAndBadTime() = runBlocking {
        try {
            repo.enable(ownerA, 13, "20:00")
            fail("non-template fasting hours must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            repo.enable(ownerA, 16, "8pm")
            fail("non HH:mm start must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(repo.list(ownerA).isEmpty())
    }

    @Test
    fun reenablingSameTemplateAndStartIsIdempotent() = runBlocking {
        val first = repo.enable(ownerA, 16, "20:00")
        val again = repo.enable(ownerA, 16, "20:00")

        assertEquals(first.id, again.id)
        assertEquals(1, db.outbox().pending(ownerA.uid).size)
    }

    @Test
    fun enablingDifferentTemplateRetiresOldScheduleButKeepsHistory() = runBlocking {
        val first = repo.enable(ownerA, 16, "20:00")
        val second = repo.enable(ownerA, 12, "21:30")

        assertEquals(second.id, repo.getEnabled(ownerA)?.id)
        val all = repo.list(ownerA)
        assertEquals(2, all.size)
        val retired = all.single { it.id == first.id }
        assertFalse(retired.enabled)
        assertEquals(2L, retired.revision)

        // One UPSERT for the disable of the old + one for the new schedule.
        assertEquals(3, db.outbox().pending(ownerA.uid).size)
    }

    @Test
    fun disableMarksScheduleOffAndIsIdempotent() = runBlocking {
        repo.enable(ownerA, 14, "19:00")
        val disabled = repo.disable(ownerA)

        assertNotNull(disabled)
        assertFalse(disabled!!.enabled)
        assertEquals(2L, disabled.revision)
        assertNull(repo.getEnabled(ownerA))
        assertNull(repo.disable(ownerA)) // second disable is a no-op
        assertEquals(1, repo.list(ownerA).size) // history preserved
        // enable + disable = two UPSERT events
        assertEquals(2, db.outbox().pending(ownerA.uid).size)
    }
}
