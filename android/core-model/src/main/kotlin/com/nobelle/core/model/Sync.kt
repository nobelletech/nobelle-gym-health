package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Immutable sync event stored as a small chunked file in the user's Drive
 * appDataFolder (§24). Opaque filenames; dedup by operationUuid.
 */
@Serializable
data class SyncEvent(
    val schemaVersion: Int,
    val accountNamespace: String, // scoped owner uid
    val installationId: String,
    val entityId: String,
    val entityType: String,
    val operationUuid: String, // idempotent across retry duplicates
    val revision: Long,
    val parentRevision: Long? = null,
    val op: Op,
    val payloadJson: String,
    val utc: Instant,
) {
    enum class Op { UPSERT, DELETE }
}

@Serializable
data class SyncOperation(
    val id: String,
    val ownerUID: String,
    val entityId: String,
    val entityType: String,
    val operationUuid: String,
    val state: State = State.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdUtc: Instant,
) {
    enum class State { PENDING, UPLOADED, ACKED, FAILED }
}

/** Prevents deleted data reappearing after offline devices reconnect (§13). */
@Serializable
data class DeletionTombstone(
    val entityId: String,
    val entityType: String,
    val ownerUID: String,
    val deletedUtc: Instant,
    val operationUuid: String,
)
