package com.nobelle.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nobelle.core.data.repository.HealthObservationRepository
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.Provenance
import com.nobelle.core.model.RecordingMethod
import com.nobelle.core.model.SourceKey
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthObservationRepositoryTest {
    private lateinit var db: NobelleDatabase
    private lateinit var repo: HealthObservationRepository

    private var tick = 0L
    private var idCounter = 0
    private fun now() = Instant.fromEpochMilliseconds(1_700_000_000_000L + (tick++) * 1000)
    private fun newId() = "op-${idCounter++}"

    private val ownerA = OwnerId("https://accounts.google.com", "subject-a")
    private val ownerB = OwnerId("https://accounts.google.com", "subject-b")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobelleDatabase::class.java).build()
        repo = HealthObservationRepository(db, "install-test", now = ::now, newId = ::newId)
    }

    @After
    fun tearDown() = db.close()

    private fun observation(
        id: String,
        ownerUID: String,
        value: Double = 1234.0,
        importedAt: Instant = Instant.fromEpochMilliseconds(1_700_000_100_000),
    ) = HealthObservation(
        id = id,
        ownerUID = ownerUID,
        marker = "steps",
        group = MarkerGroup.ACTIVITY,
        value = value,
        unit = "count",
        rawValue = value,
        rawUnit = "count",
        measuredUtc = Instant.fromEpochMilliseconds(1_700_000_000_000),
        timeZoneOffsetSec = -14_400,
        provenance = Provenance.MEASURED_IMPORTED,
        method = RecordingMethod.SOURCE_SYNC,
        source = SourceKey(
            sourceNamespace = "health_connect:com.example.source",
            sourceRecordId = "record-1",
            originDevice = "Pixel Watch",
            importRoute = "Health Connect",
        ),
        context = mapOf("healthConnectCategory" to "STEPS"),
        importedUtc = importedAt,
    )

    private suspend fun pendingHealthOps(owner: OwnerId) =
        db.outbox().pending(owner.uid).filter { it.entityType == "health_observation" }

    @Test
    fun identicalReimportIsANoOpWithOneOutboxOperation() = runBlocking {
        val first = repo.upsertImported(ownerA, observation("obs-1", ownerA.uid))
        assertTrue(first.second)
        assertEquals(1L, first.first.revision)

        // importedUtc is import metadata, not source identity — a re-import
        // with a different import timestamp is still the same source record.
        val again = repo.upsertImported(
            ownerA,
            observation("obs-1", ownerA.uid, importedAt = Instant.fromEpochMilliseconds(1_800_000_000_000)),
        )
        assertFalse(again.second)
        assertEquals(first.first.id, again.first.id)
        assertEquals(1L, again.first.revision)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(1, pendingHealthOps(ownerA).size)
    }

    @Test
    fun changedSourceValueBumpsRevisionAndQueuesAnotherOperation() = runBlocking {
        repo.upsertImported(ownerA, observation("obs-1", ownerA.uid, value = 1234.0))
        val updated = repo.upsertImported(ownerA, observation("obs-1", ownerA.uid, value = 1500.0))

        assertTrue(updated.second)
        assertEquals(2L, updated.first.revision)
        assertEquals(1500.0, updated.first.value, 0.0)
        val stored = repo.get(ownerA, "obs-1")
        assertNotNull(stored)
        assertEquals(1500.0, stored!!.value, 0.0)
        assertEquals(2L, stored.revision)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(2, pendingHealthOps(ownerA).size)
    }

    @Test
    fun sameDeterministicIdIsIsolatedPerOwner() = runBlocking {
        repo.upsertImported(ownerA, observation("obs-1", ownerA.uid, value = 1234.0))
        val forB = repo.upsertImported(ownerB, observation("obs-1", ownerB.uid, value = 77.0))

        assertTrue(forB.second)
        // Same deterministic ID, two rows: owner-scoped reads keep them apart.
        val forA = repo.get(ownerA, "obs-1")!!
        val forBStored = repo.get(ownerB, "obs-1")!!
        assertEquals(ownerA.uid, forA.ownerUID)
        assertEquals(ownerB.uid, forBStored.ownerUID)
        assertEquals(1234.0, forA.value, 0.0)
        assertEquals(77.0, forBStored.value, 0.0)
        assertEquals(1, repo.list(ownerA).size)
        assertEquals(1, repo.list(ownerB).size)
        assertEquals(1, pendingHealthOps(ownerA).size)
        assertEquals(1, pendingHealthOps(ownerB).size)
    }

    @Test
    fun deletedRowReimportRevivesWithNextRevision() = runBlocking {
        repo.upsertImported(ownerA, observation("obs-1", ownerA.uid))
        repo.delete(ownerA, "obs-1")
        assertTrue(repo.list(ownerA).isEmpty())

        val revived = repo.upsertImported(ownerA, observation("obs-1", ownerA.uid))
        assertTrue(revived.second)
        assertNull(revived.first.deletedUtc)
        assertEquals(3L, revived.first.revision)
        assertEquals(1, repo.list(ownerA).size)
    }
}
