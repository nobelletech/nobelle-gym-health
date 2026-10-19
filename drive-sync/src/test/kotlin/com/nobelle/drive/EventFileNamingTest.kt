package com.nobelle.drive

import com.nobelle.core.model.SyncEvent
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventFileNamingTest {
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
    fun `operation uuid round trips through the filename`() {
        assertEquals("op-9", EventFileNaming.operationUuidFrom(EventFileNaming.nameFor(event)))
    }

    @Test
    fun `operation uuid survives uuids containing extra segments`() {
        val uuid = "3f6f0b9a-1234-5678-9abc-def012345678"
        val name = EventFileNaming.nameFor(event.copy(operationUuid = uuid))
        assertEquals(uuid, EventFileNaming.operationUuidFrom(name))
    }

    @Test
    fun `malformed names return null`() {
        assertNull(EventFileNaming.operationUuidFrom("random.json"))
        assertNull(EventFileNaming.operationUuidFrom("ev_x.json")) // no uuid segment
        assertNull(EventFileNaming.operationUuidFrom("ev_inst_123_.json")) // empty uuid
        assertNull(EventFileNaming.operationUuidFrom("ev_inst_123_op-9.txt"))
        assertNull(EventFileNaming.operationUuidFrom(""))
    }
}
