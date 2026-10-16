package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore

/**
 * Client-side sync engine (§13/§24). Uploads immutable SyncEvent chunks to the
 * user's own Drive appDataFolder and rebuilds local index from remote files.
 * Drive is file storage — not a transactional database (§24).
 */
class SyncEngine(
    private val drive: DriveStore,
    private val outbox: OutboxWriter,
    private val applier: RemoteApplier,
) {
    interface OutboxWriter {
        suspend fun pending(limit: Int): List<SyncEvent>
        suspend fun markUploaded(operationUuid: String)
        suspend fun markFailed(operationUuid: String, error: String)
    }

    interface RemoteApplier {
        suspend fun seenOperation(operationUuid: String): Boolean
        suspend fun apply(event: SyncEvent)
    }

    /** Drain the local outbox. Successful Drive write is acked separately (§13). */
    suspend fun upload(limit: Int = 50) {
        for (event in outbox.pending(limit)) {
            try {
                drive.putEvent(event)
                outbox.markUploaded(event.operationUuid)
            } catch (e: Exception) {
                outbox.markFailed(event.operationUuid, e.message ?: "upload failed")
            }
        }
    }

    /** Pull remote event files, deduping retries by operation UUID (§24). */
    suspend fun download(cursor: String? = null): String? {
        val page = drive.listEvents(cursor)
        for (file in page.files) {
            val event = drive.getEvent(file.id) ?: continue
            if (!applier.seenOperation(event.operationUuid)) {
                applier.apply(event)
            }
        }
        return page.nextCursor
    }
}
