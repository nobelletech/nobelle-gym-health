package com.nobelle.core.sync

import com.nobelle.core.data.OutboxDao
import com.nobelle.core.model.SyncEvent
import kotlinx.serialization.json.Json

/**
 * Adapts the Room [OutboxDao] to [SyncEngine.OutboxWriter] so the engine's
 * upload drain works directly on the phone's encrypted outbox table. Rows
 * hold the serialized SyncEvent in `payloadJson`; a row that fails to decode
 * is skipped rather than crashing the drain — it stays PENDING for diagnosis.
 */
class OutboxDaoWriter(
    private val dao: OutboxDao,
    private val ownerUid: String,
) : SyncEngine.OutboxWriter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pending(limit: Int): List<SyncEvent> =
        dao.pending(ownerUid, limit).mapNotNull { row ->
            runCatching {
                json.decodeFromString(SyncEvent.serializer(), row.payloadJson)
            }.getOrNull()
        }

    override suspend fun markUploaded(operationUuid: String) {
        dao.markState(ownerUid, operationUuid, "UPLOADED")
    }

    override suspend fun markFailed(operationUuid: String, error: String) {
        // Stay PENDING: the upload failure may be transient (network, expired
        // token), and nothing else resurrects FAILED rows — stranding an
        // event would lose the workout. attempts/lastError still advance so a
        // later scheduler can cap retries.
        dao.markState(ownerUid, operationUuid, "PENDING", error)
    }
}
