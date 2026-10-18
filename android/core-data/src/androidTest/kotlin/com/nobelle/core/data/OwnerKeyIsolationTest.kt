package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.model.OwnerId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Composite (ownerUID, id) keys must let the same entity id exist once per
 *  owner without INSERT OR REPLACE ever crossing the account boundary. */
@RunWith(AndroidJUnit4::class)
class OwnerKeyIsolationTest {
    private lateinit var db: NobelleDatabase

    private val ownerA = OwnerId("https://accounts.google.com", "subject-a").uid
    private val ownerB = OwnerId("https://accounts.google.com", "subject-b").uid

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun sameWorkoutSessionIdCoexistsForBothOwners() = runBlocking {
        db.sessions().upsert(
            WorkoutSessionEntity(ownerA, "shared-id", "ACTIVE", 1L, null, 1L, """{"id":"shared-id","owner":"a"}""", 1L)
        )
        db.sessions().upsert(
            WorkoutSessionEntity(ownerB, "shared-id", "COMPLETED", 2L, 3L, 5L, """{"id":"shared-id","owner":"b"}""", 4L)
        )

        val a = db.sessions().byId(ownerA, "shared-id")!!
        val b = db.sessions().byId(ownerB, "shared-id")!!
        assertEquals("ACTIVE", a.state)
        assertEquals("COMPLETED", b.state)
        assertEquals("""{"id":"shared-id","owner":"a"}""", a.payloadJson)
        assertEquals("""{"id":"shared-id","owner":"b"}""", b.payloadJson)
        // B's later upsert did not touch A's row.
        assertEquals(1L, a.sequence)
        assertTrue(db.sessions().list(ownerA).size == 1)
        assertTrue(db.sessions().list(ownerB).size == 1)
    }

    @Test
    fun sameGymVisitIdCoexistsForBothOwners() = runBlocking {
        db.gymVisits().upsert(
            GymVisitEntity(ownerA, "shared-id", 1L, null, "2023-11-14", true, """{"owner":"a"}""", 1L, null)
        )
        db.gymVisits().upsert(
            GymVisitEntity(ownerB, "shared-id", 2L, null, "2023-11-15", false, """{"owner":"b"}""", 7L, null)
        )

        assertEquals(1L, db.gymVisits().byId(ownerA, "shared-id")!!.revision)
        assertEquals(7L, db.gymVisits().byId(ownerB, "shared-id")!!.revision)
        db.gymVisits().hardDelete(ownerA, "shared-id")
        assertNull(db.gymVisits().byId(ownerA, "shared-id"))
        assertEquals(7L, db.gymVisits().byId(ownerB, "shared-id")!!.revision)
    }

    @Test
    fun sameFastingSessionIdCoexistsForBothOwners() = runBlocking {
        db.fastingSessions().upsert(
            FastingSessionEntity(ownerA, "shared-id", 1L, null, "ACTIVE", """{"owner":"a"}""", 1L, null)
        )
        db.fastingSessions().upsert(
            FastingSessionEntity(ownerB, "shared-id", 2L, 4L, "COMPLETED", """{"owner":"b"}""", 3L, null)
        )

        assertEquals("ACTIVE", db.fastingSessions().byId(ownerA, "shared-id")!!.status)
        assertEquals("COMPLETED", db.fastingSessions().byId(ownerB, "shared-id")!!.status)
        assertEquals("shared-id", db.fastingSessions().active(ownerA)!!.id)
        assertNull(db.fastingSessions().active(ownerB))
    }

    @Test
    fun sameHealthObservationIdCoexistsForBothOwners() = runBlocking {
        db.healthObservations().upsert(
            HealthObservationEntity(ownerA, "shared-id", "weight", 1L, null, null, """{"owner":"a"}""", 1L, null)
        )
        db.healthObservations().upsert(
            HealthObservationEntity(ownerB, "shared-id", "weight", 2L, "hc", "r1", """{"owner":"b"}""", 9L, null)
        )

        assertEquals(1L, db.healthObservations().byId(ownerA, "shared-id")!!.revision)
        assertEquals(9L, db.healthObservations().byId(ownerB, "shared-id")!!.revision)
        assertNull(db.healthObservations().byId(ownerA, "shared-id")!!.sourceNamespace)
        assertEquals("hc", db.healthObservations().byId(ownerB, "shared-id")!!.sourceNamespace)
    }
}
