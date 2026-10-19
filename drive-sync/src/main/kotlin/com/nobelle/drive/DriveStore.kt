package com.nobelle.drive

import com.nobelle.core.model.SyncEvent
import kotlinx.serialization.json.Json

/**
 * Storage boundary for the user's own Google Drive appDataFolder (§24).
 * Implementations: Android (Drive API + user's OAuth) and web (GIS token).
 * There is no Nobelle backend; this is the only cloud destination.
 */
interface DriveStore {
    /** Write an immutable event chunk. Retries may create duplicate objects;
     *  ingest dedupes by operationUuid. */
    suspend fun putEvent(event: SyncEvent): RemoteFile

    suspend fun getEvent(fileId: String): SyncEvent?

    /** Bounded paging; cursor is opaque. On expiry, caller rescans (§13). */
    suspend fun listEvents(cursor: String? = null): Page

    suspend fun deleteFile(fileId: String)

    data class RemoteFile(val id: String, val name: String)
    data class Page(val files: List<RemoteFile>, val nextCursor: String?)
}

/** Opaque filenames carry no medical labels (§24). */
object EventFileNaming {
    fun nameFor(event: SyncEvent): String =
        "ev_${event.installationId}_${event.utc.toEpochMilliseconds()}_${event.operationUuid}.json"

    /**
     * Recovers the operation UUID embedded by [nameFor] so a download pass can
     * dedupe already-applied operations without fetching the file body.
     * Returns null for anything outside the `ev_*_<uuid>.json` shape.
     */
    fun operationUuidFrom(name: String): String? {
        if (!name.startsWith("ev_") || !name.endsWith(".json")) return null
        val lastUnderscore = name.lastIndexOf('_')
        if (lastUnderscore <= 2) return null
        return name.substring(lastUnderscore + 1, name.length - ".json".length)
            .takeIf { it.isNotEmpty() }
    }
}

object EventCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(event: SyncEvent): ByteArray = json.encodeToString(event).encodeToByteArray()
    fun decode(bytes: ByteArray): SyncEvent = json.decodeFromString(bytes.decodeToString())
}
