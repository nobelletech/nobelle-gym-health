package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveRoundTripDiagnosticTest {
    @Test
    fun `round trip uploads reads and deletes non-personal event`() = runBlocking {
        val drive = FakeDrive()

        val result = DriveRoundTripDiagnostic(drive).run()

        assertTrue(result.deleted)
        assertEquals(1, drive.deleted.size)
        assertTrue(drive.uploaded!!.payloadJson.contains("\"personalData\":false"))
    }

    @Test
    fun `read mismatch still deletes temporary file`() = runBlocking {
        val drive = FakeDrive(mismatch = true)

        val error = runCatching { DriveRoundTripDiagnostic(drive).run() }.exceptionOrNull()

        assertTrue(error!!.message!!.contains("did not match"))
        assertEquals(listOf("file-1"), drive.deleted)
    }

    private class FakeDrive(private val mismatch: Boolean = false) : DriveStore {
        var uploaded: SyncEvent? = null
        val deleted = mutableListOf<String>()

        override suspend fun putEvent(event: SyncEvent): DriveStore.RemoteFile {
            uploaded = event
            return DriveStore.RemoteFile("file-1", "opaque.json")
        }

        override suspend fun getEvent(fileId: String): SyncEvent? =
            if (mismatch) uploaded!!.copy(operationUuid = "wrong") else uploaded

        override suspend fun listEvents(cursor: String?) = DriveStore.Page(emptyList(), null)

        override suspend fun deleteFile(fileId: String) {
            deleted += fileId
        }
    }
}
