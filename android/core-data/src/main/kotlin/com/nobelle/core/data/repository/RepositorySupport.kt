package com.nobelle.core.data.repository

import com.nobelle.core.data.SyncOutboxEntity
import com.nobelle.core.model.SyncEvent
import kotlinx.serialization.json.Json

/** Single canonical codec for durable payloads and sync events. */
internal val durableJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal const val SYNC_EVENT_SCHEMA_VERSION = 1

/**
 * Persisted outbox row wraps the serialized immutable SyncEvent. Public so
 * the phone's watch-relay (SyncListenerService) can enqueue verified watch
 * events through the identical path the repositories use.
 */
fun SyncEvent.toOutboxEntity(createdUtcMillis: Long) = SyncOutboxEntity(
    operationUuid = operationUuid,
    ownerUID = accountNamespace,
    entityId = entityId,
    entityType = entityType,
    state = "PENDING",
    attempts = 0,
    lastError = null,
    createdUtc = createdUtcMillis,
    payloadJson = durableJson.encodeToString(SyncEvent.serializer(), this),
)
