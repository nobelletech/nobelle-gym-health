package com.nobelle.core.model

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Watch -> phone workout-event sync over the Wear OS Data Layer (§7/§13/§24).
 *
 * The watch records a timed workout, wraps it in a [WatchSyncEvent] and sends
 * it to the enrolled phone on [SyncPaths.EVENT]. Every event is authenticated
 * with HMAC-SHA256 keyed by the enrollment token derived during pairing — the
 * token itself never travels. The phone verifies the tag, relays the event
 * into its encrypted outbox as a regular [SyncEvent], then answers with
 * [WatchSyncAck] on [SyncPaths.ACK] so the watch can drop its local copy.
 *
 * The tag signs the canonical JSON of [WatchSyncEventBody] — the same fields
 * as the wire event minus `tag` — produced by the single shared [WatchSyncCodec]
 * (ignoreUnknownKeys + encodeDefaults). Because body serialization is
 * canonical, the phone recomputes the exact signed bytes from the decoded
 * fields; any tampered field changes the body and invalidates the tag.
 */
object SyncPaths {
    const val PREFIX = "/nobelle/sync/v1/"
    const val EVENT = PREFIX + "event"
    const val ACK = PREFIX + "ack"
    const val SCHEMA_VERSION = 1
    const val ENTITY_TYPE_WATCH_WORKOUT = "watch_workout"
}

/** The fields an event carries, WITHOUT the authentication tag. This — and
 *  only this — is what the HMAC signs. */
@Serializable
data class WatchSyncEventBody(
    val schemaVersion: Int = SyncPaths.SCHEMA_VERSION,
    val watchInstallationId: String,
    val operationUuid: String,
    val entityId: String,
    val entityType: String,
    val payloadJson: String,
    val utcEpochMs: Long,
)

/** Wire event watch -> phone: the signed body plus its base64 HMAC tag. */
@Serializable
data class WatchSyncEvent(
    val schemaVersion: Int = SyncPaths.SCHEMA_VERSION,
    val watchInstallationId: String,
    val operationUuid: String,
    val entityId: String,
    val entityType: String,
    val payloadJson: String,
    val utcEpochMs: Long,
    val tag: String,
) {
    /** Rebuilds the unsigned body that the tag authenticates. */
    fun signedBody() = WatchSyncEventBody(
        schemaVersion = schemaVersion,
        watchInstallationId = watchInstallationId,
        operationUuid = operationUuid,
        entityId = entityId,
        entityType = entityType,
        payloadJson = payloadJson,
        utcEpochMs = utcEpochMs,
    )
}

/** Phone -> watch: confirms the outbox insert for [operationUuid] committed. */
@Serializable
data class WatchSyncAck(
    val schemaVersion: Int = SyncPaths.SCHEMA_VERSION,
    val operationUuid: String,
)

/** Domain payload carried inside a watch workout sync event (payloadJson). */
@Serializable
data class WatchWorkoutPayload(
    val schemaVersion: Int = 1,
    val id: String,
    val activity: String = "workout",
    val startedUtc: Long,
    val endedUtc: Long,
    val durationSec: Long,
    val hrSampleCount: Int,
    val avgBpm: Double? = null,
    val maxBpm: Double? = null,
)

/**
 * The watch's in-progress session record, persisted wrapped to
 * `watch_workout_active.v1` so an ACTIVE session survives process death.
 * Heart-rate accumulation is a count + running sum + max — individual samples
 * are never stored.
 */
@Serializable
data class ActiveWatchWorkout(
    val schemaVersion: Int = 1,
    val id: String,
    val startedUtcMs: Long,
    val hrSampleCount: Int = 0,
    val hrSumBpm: Double = 0.0,
    val maxBpm: Double? = null,
) {
    fun withHeartRateSample(bpm: Double): ActiveWatchWorkout = copy(
        hrSampleCount = hrSampleCount + 1,
        hrSumBpm = hrSumBpm + bpm,
        maxBpm = maxOf(maxBpm ?: bpm, bpm),
    )

    /** Builds the wire payload at finish time. Averages only when real
     *  samples exist — a session without permission records 0 samples. */
    fun toPayload(endedUtcMs: Long): WatchWorkoutPayload = WatchWorkoutPayload(
        id = id,
        startedUtc = startedUtcMs,
        endedUtc = endedUtcMs,
        durationSec = maxOf(0L, (endedUtcMs - startedUtcMs) / 1_000),
        hrSampleCount = hrSampleCount,
        avgBpm = if (hrSampleCount > 0) hrSumBpm / hrSampleCount else null,
        maxBpm = maxBpm,
    )
}

/**
 * Canonical codec + authentication for the watch sync wire protocol. Pure
 * java.security/javax.crypto so identical code runs and is unit-tested on
 * both devices without Android.
 */
object WatchSyncCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeWorkoutPayload(payload: WatchWorkoutPayload): String =
        json.encodeToString(WatchWorkoutPayload.serializer(), payload)

    fun decodeWorkoutPayload(text: String): WatchWorkoutPayload =
        json.decodeFromString(WatchWorkoutPayload.serializer(), text)

    /** Canonical body bytes — the exact message the HMAC signs. */
    fun encodeBody(body: WatchSyncEventBody): ByteArray =
        json.encodeToString(WatchSyncEventBody.serializer(), body).encodeToByteArray()

    fun decodeBody(bytes: ByteArray): WatchSyncEventBody =
        json.decodeFromString(WatchSyncEventBody.serializer(), bytes.decodeToString())

    /** Raw HMAC-SHA256 tag of the canonical body, keyed by the enrollment token. */
    fun tagBytes(token: ByteArray, body: WatchSyncEventBody): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(token, "HmacSHA256"))
            doFinal(encodeBody(body))
        }

    /** Produces the wire form: body fields plus the base64 authentication tag. */
    fun encodeEvent(token: ByteArray, body: WatchSyncEventBody): ByteArray =
        json.encodeToString(
            WatchSyncEvent.serializer(),
            WatchSyncEvent(
                schemaVersion = body.schemaVersion,
                watchInstallationId = body.watchInstallationId,
                operationUuid = body.operationUuid,
                entityId = body.entityId,
                entityType = body.entityType,
                payloadJson = body.payloadJson,
                utcEpochMs = body.utcEpochMs,
                tag = EnrollCrypto.toBase64(tagBytes(token, body)),
            ),
        ).encodeToByteArray()

    fun decodeEvent(bytes: ByteArray): WatchSyncEvent =
        json.decodeFromString(WatchSyncEvent.serializer(), bytes.decodeToString())

    /**
     * Constant-time verification of an event's tag against the enrollment
     * token. A wrong token, a tampered field, a malformed tag or a schema
     * mismatch all fail — callers drop silently rather than asking why.
     */
    fun verify(token: ByteArray, event: WatchSyncEvent): Boolean {
        if (event.schemaVersion != SyncPaths.SCHEMA_VERSION) return false
        val supplied =
            runCatching { EnrollCrypto.fromBase64(event.tag) }.getOrNull()
                ?: return false
        return MessageDigest.isEqual(tagBytes(token, event.signedBody()), supplied)
    }

    fun encodeAck(ack: WatchSyncAck): ByteArray =
        json.encodeToString(WatchSyncAck.serializer(), ack).encodeToByteArray()

    fun decodeAck(bytes: ByteArray): WatchSyncAck =
        json.decodeFromString(WatchSyncAck.serializer(), bytes.decodeToString())

    /**
     * Phone-side relay: a verified watch event becomes a regular SyncEvent so
     * the encrypted outbox and Drive drain treat it like any other operation.
     */
    fun toSyncEvent(event: WatchSyncEvent, ownerUid: String): SyncEvent = SyncEvent(
        schemaVersion = SYNC_EVENT_SCHEMA_VERSION,
        accountNamespace = ownerUid,
        installationId = event.watchInstallationId,
        entityId = event.entityId,
        entityType = event.entityType,
        operationUuid = event.operationUuid,
        revision = 1,
        parentRevision = null,
        op = SyncEvent.Op.UPSERT,
        payloadJson = event.payloadJson,
        utc = Instant.fromEpochMilliseconds(event.utcEpochMs),
    )

    private const val SYNC_EVENT_SCHEMA_VERSION = 1
}
