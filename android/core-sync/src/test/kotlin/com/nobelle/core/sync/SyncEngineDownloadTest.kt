package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore
import com.nobelle.drive.EventFileNaming
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineDownloadTest {
    private val owner = "accounts.google.com:subject-a"

    private fun event(uuid: String, namespace: String = owner) = SyncEvent(
        schemaVersion = 1,
        accountNamespace = namespace,
        installationId = "install-remote",
        entityId = "e-$uuid",
        entityType = "gym_visit",
        operationUuid = uuid,
        revision = 1,
        op = SyncEvent.Op.UPSERT,
        payloadJson = "{}",
        utc = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    @Test
    fun `already-applied op is skipped by filename without fetching the body`() = runBlocking {
        val seen = event("op-applied")
        val drive = FakeDrive(
            files = mapOf("f1" to seen),
            names = mapOf("f1" to EventFileNaming.nameFor(seen)),
        )
        val applier = FakeApplier(seenUuids = setOf("op-applied"))

        SyncEngine(drive, FakeOutboxWriter(), applier).download()

        assertEquals(emptyList<String>(), drive.fetched)
        assertTrue(applier.applied.isEmpty())
    }

    @Test
    fun `one malformed file does not abort the page`() = runBlocking {
        val bad = event("op-bad")
        val good = event("op-good")
        val drive = FakeDrive(
            files = mapOf("f1" to bad, "f2" to good),
            names = mapOf(
                "f1" to EventFileNaming.nameFor(bad),
                "f2" to EventFileNaming.nameFor(good),
            ),
            failing = setOf("f1"),
        )
        val applier = FakeApplier()

        SyncEngine(drive, FakeOutboxWriter(), applier).download()

        assertEquals(listOf("op-good"), applier.applied.map { it.operationUuid })
    }

    @Test
    fun `foreign namespace events are never applied`() = runBlocking {
        val foreign = event("op-foreign", namespace = "accounts.google.com:subject-b")
        val drive = FakeDrive(files = mapOf("f1" to foreign))
        val applier = FakeApplier()

        SyncEngine(drive, FakeOutboxWriter(), applier, accountNamespace = owner).download()

        assertTrue(applier.applied.isEmpty())
        assertTrue(applier.seenChecks.isEmpty())
    }

    @Test
    fun `paging follows cursors and applies each page`() = runBlocking {
        val first = event("op-1")
        val second = event("op-2")
        val drive = FakeDrive(
            files = mapOf("f1" to first),
            extraPages = mapOf("next" to DriveStore.Page(
                listOf(DriveStore.RemoteFile("f2", EventFileNaming.nameFor(second))),
                null,
            )),
            extraFiles = mapOf("f2" to second),
        )
        val applier = FakeApplier()
        val engine = SyncEngine(drive, FakeOutboxWriter(), applier, accountNamespace = owner)

        var cursor: String? = null
        var passes = 0
        do {
            cursor = engine.download(cursor)
            passes++
        } while (cursor != null)

        assertEquals(2, passes)
        assertEquals(listOf("op-1", "op-2"), applier.applied.map { it.operationUuid })
    }

    private class FakeOutboxWriter : SyncEngine.OutboxWriter {
        override suspend fun pending(limit: Int) = emptyList<SyncEvent>()
        override suspend fun markUploaded(operationUuid: String) = Unit
        override suspend fun markFailed(operationUuid: String, error: String) = Unit
    }

    private class FakeApplier(
        private val seenUuids: Set<String> = emptySet(),
    ) : SyncEngine.RemoteApplier {
        val seenChecks = mutableListOf<String>()
        val applied = mutableListOf<SyncEvent>()
        override suspend fun seenOperation(operationUuid: String): Boolean {
            seenChecks += operationUuid
            return operationUuid in seenUuids || applied.any { it.operationUuid == operationUuid }
        }
        override suspend fun apply(event: SyncEvent) {
            applied += event
        }
    }

    private class FakeDrive(
        private val files: Map<String, SyncEvent>,
        private val names: Map<String, String> = emptyMap(),
        private val failing: Set<String> = emptySet(),
        private val extraPages: Map<String, DriveStore.Page> = emptyMap(),
        private val extraFiles: Map<String, SyncEvent> = emptyMap(),
    ) : DriveStore {
        val fetched = mutableListOf<String>()

        override suspend fun putEvent(event: SyncEvent) =
            DriveStore.RemoteFile("new", EventFileNaming.nameFor(event))

        override suspend fun getEvent(fileId: String): SyncEvent? {
            fetched += fileId
            if (fileId in failing) throw IllegalStateException("corrupt chunk")
            return (files + extraFiles)[fileId]
        }

        override suspend fun listEvents(cursor: String?): DriveStore.Page {
            if (cursor != null) return extraPages[cursor] ?: DriveStore.Page(emptyList(), null)
            return DriveStore.Page(
                files.map { (id, ev) -> DriveStore.RemoteFile(id, names[id] ?: ev.entityId) },
                nextCursor = if (extraPages.isEmpty()) null else extraPages.keys.first(),
            )
        }

        override suspend fun deleteFile(fileId: String) = Unit
    }
}
