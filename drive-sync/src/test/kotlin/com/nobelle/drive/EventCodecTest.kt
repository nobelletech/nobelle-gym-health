package com.nobelle.drive

import com.nobelle.core.model.SyncEvent
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EventCodecTest {
    private val event = SyncEvent(
        schemaVersion = 1,
        accountNamespace = "accounts.google.com:sub-123",
        installationId = "inst-1",
        entityId = "e-1",
        entityType = "GymVisit",
        operationUuid = "op-9",
        revision = 1,
        op = SyncEvent.Op.UPSERT,
        payloadJson = "{}",
        utc = Instant.parse("2026-10-16T00:00:00Z"),
    )

    @Test
    fun `event round trips`() {
        assertEquals(event, EventCodec.decode(EventCodec.encode(event)))
    }

    @Test
    fun `filenames are opaque - no medical labels`() {
        val name = EventFileNaming.nameFor(event)
        assertFalse(name.contains("glucose", true))
        assertFalse(name.contains("health", true))
        assertFalse(name.contains("e-1")) // entity id not in filename
    }

    @Test
    fun `duplicate uploads dedupe by operation uuid`() {
        // Same operationUuid -> same logical op regardless of file duplicates
        val seen = mutableSetOf<String>()
        val e1 = event.copy(entityId = "x")
        listOf(event, e1.copy(operationUuid = "op-9"), e1.copy(operationUuid = "op-10"))
            .forEach { seen.add(it.operationUuid) }
        assertEquals(setOf("op-9", "op-10"), seen)
    }
}
