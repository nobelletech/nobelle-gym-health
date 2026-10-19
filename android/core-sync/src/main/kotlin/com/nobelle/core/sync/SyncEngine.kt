package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore
import com.nobelle.drive.EventFileNaming

/**
 * Client-side sync engine (§13/§24). Uploads immutable SyncEvent chunks to the
 * user's own Drive appDataFolder and rebuilds local index from remote files.
 * Drive is file storage — not a transactional database (§24).
 */
class SyncEngine(
    private val drive: DriveStore,
    private val outbox: OutboxWriter,
    private val applier: RemoteApplier,
    private val accountNamespace: String? = null,
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

    /**
     * Drain the local outbox. Successful Drive write is acked separately
     * (§13). Returns how many events were uploaded so callers can stop
     * when a batch makes no progress (e.g. expired token).
     */
    suspend fun upload(limit: Int = 50): Int {
        var uploaded = 0
        for (event in outbox.pending(limit)) {
            try {
                drive.putEvent(event)
                outbox.markUploaded(event.operationUuid)
                uploaded++
            } catch (e: Exception) {
                outbox.markFailed(event.operationUuid, e.message ?: "upload failed")
            }
        }
        return uploaded
    }

    /**
     * Pull remote event files, deduping retries by operation UUID (§24).
     * The filename already carries the operation UUID, so a previously
     * applied op is skipped without downloading its body; a malformed or
     * foreign-namespace file never aborts the page.
     */
    suspend fun download(cursor: String? = null): String? {
        val page = drive.listEvents(cursor)
        for (file in page.files) {
            val namedUuid = EventFileNaming.operationUuidFrom(file.name)
            if (namedUuid != null && applier.seenOperation(namedUuid)) continue
            val event = runCatching { drive.getEvent(file.id) }.getOrNull() ?: continue
            // appDataFolder is already per-account; the namespace check is
            // defense-in-depth so a stray file can never write foreign rows.
            if (accountNamespace != null && event.accountNamespace != accountNamespace) continue
            if (!applier.seenOperation(event.operationUuid)) {
                applier.apply(event)
            }
        }
        return page.nextCursor
    }
}
