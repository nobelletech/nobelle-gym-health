package com.nobelle.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSyncTest {

    private val token = ByteArray(32) { (it * 7 + 1).toByte() }
    private val otherToken = ByteArray(32) { (it * 13 + 3).toByte() }

    private fun body(payloadJson: String = "{\"id\":\"w-1\"}") = WatchSyncEventBody(
        watchInstallationId = "watch-install-1",
        operationUuid = "op-uuid-1",
        entityId = "w-1",
        entityType = SyncPaths.ENTITY_TYPE_WATCH_WORKOUT,
        payloadJson = payloadJson,
        utcEpochMs = 1_700_000_000_000,
    )

    // ---- Wire codec ----

    @Test
    fun `event survives an encode-decode round trip`() {
        val encoded = WatchSyncCodec.encodeEvent(token, body())
        val decoded = WatchSyncCodec.decodeEvent(encoded)
        assertEquals(body(), decoded.signedBody())
        assertTrue(decoded.tag.isNotBlank())
        assertTrue(WatchSyncCodec.verify(token, decoded))
    }

    @Test
    fun `tag is deterministic for the same body and token`() {
        val first = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        val second = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        assertEquals(first.tag, second.tag)
    }

    @Test
    fun `ack survives an encode-decode round trip`() {
        val ack = WatchSyncAck(operationUuid = "op-uuid-9")
        val decoded = WatchSyncCodec.decodeAck(WatchSyncCodec.encodeAck(ack))
        assertEquals(ack, decoded)
        assertEquals(SyncPaths.SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `wire paths live under the versioned sync prefix`() {
        assertTrue(SyncPaths.EVENT.startsWith(SyncPaths.PREFIX))
        assertTrue(SyncPaths.ACK.startsWith(SyncPaths.PREFIX))
        assertEquals("/nobelle/sync/v1/", SyncPaths.PREFIX)
    }

    // ---- HMAC accept / reject ----

    @Test
    fun `a wrong enrollment token is rejected`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        assertFalse(WatchSyncCodec.verify(otherToken, event))
    }

    @Test
    fun `a tampered field is rejected`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        val tampered = event.copy(payloadJson = "{\"id\":\"w-forged\"}")
        assertFalse(WatchSyncCodec.verify(token, tampered))
    }

    @Test
    fun `a tampered identity field is rejected`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        val tampered = event.copy(watchInstallationId = "other-watch")
        assertFalse(WatchSyncCodec.verify(token, tampered))
    }

    @Test
    fun `a malformed tag is rejected`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        assertFalse(WatchSyncCodec.verify(token, event.copy(tag = "not-base64!!!")))
        assertFalse(WatchSyncCodec.verify(token, event.copy(tag = "")))
    }

    @Test
    fun `a schema version mismatch is rejected`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        assertFalse(WatchSyncCodec.verify(token, event.copy(schemaVersion = 99)))
    }

    @Test
    fun `a token derived through enrollment drives a valid tag`() {
        // Full enrollment handshake: both sides derive the same 32-byte token.
        val watch = EnrollCrypto.generateKeyPair()
        val phone = EnrollCrypto.generateKeyPair()
        val watchNonce = EnrollCrypto.generateNonce()
        val phoneNonce = EnrollCrypto.generateNonce()
        val watchToken = EnrollCrypto.deriveEnrollmentToken(
            watch.private,
            EnrollCrypto.encodePubKeyX962(phone.public),
            watchNonce,
            phoneNonce,
        )
        val phoneToken = EnrollCrypto.deriveEnrollmentToken(
            phone.private,
            EnrollCrypto.encodePubKeyX962(watch.public),
            watchNonce,
            phoneNonce,
        )
        // The watch signs with its token; the phone verifies with its own.
        val event =
            WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(watchToken, body()))
        assertTrue(WatchSyncCodec.verify(phoneToken, event))
        // A token from a different handshake does not verify.
        val replay = EnrollCrypto.deriveEnrollmentToken(
            EnrollCrypto.generateKeyPair().private,
            EnrollCrypto.encodePubKeyX962(EnrollCrypto.generateKeyPair().public),
            EnrollCrypto.generateNonce(),
            EnrollCrypto.generateNonce(),
        )
        assertFalse(WatchSyncCodec.verify(replay, event))
    }

    // ---- Phone-side SyncEvent construction ----

    @Test
    fun `toSyncEvent maps a verified watch event into the outbox shape`() {
        val event = WatchSyncCodec.decodeEvent(WatchSyncCodec.encodeEvent(token, body()))
        val syncEvent = WatchSyncCodec.toSyncEvent(event, ownerUid = "issuer:subject")
        assertEquals(1, syncEvent.schemaVersion)
        assertEquals("issuer:subject", syncEvent.accountNamespace)
        assertEquals("watch-install-1", syncEvent.installationId)
        assertEquals("w-1", syncEvent.entityId)
        assertEquals(SyncPaths.ENTITY_TYPE_WATCH_WORKOUT, syncEvent.entityType)
        assertEquals("op-uuid-1", syncEvent.operationUuid)
        assertEquals(1L, syncEvent.revision)
        assertNull(syncEvent.parentRevision)
        assertEquals(SyncEvent.Op.UPSERT, syncEvent.op)
        assertEquals(event.payloadJson, syncEvent.payloadJson)
        assertEquals(1_700_000_000_000, syncEvent.utc.toEpochMilliseconds())
    }

    // ---- Workout payload + active session ----

    @Test
    fun `workout payload survives an encode-decode round trip`() {
        val payload = WatchWorkoutPayload(
            id = "w-1",
            startedUtc = 1_000L,
            endedUtc = 61_000L,
            durationSec = 60,
            hrSampleCount = 4,
            avgBpm = 132.5,
            maxBpm = 150.0,
        )
        val decoded = WatchSyncCodec.decodeWorkoutPayload(
            WatchSyncCodec.encodeWorkoutPayload(payload),
        )
        assertEquals(payload, decoded)
        assertEquals("workout", decoded.activity)
        assertEquals(1, decoded.schemaVersion)
    }

    @Test
    fun `finished session builds an honest payload without samples`() {
        val session = ActiveWatchWorkout(id = "w-2", startedUtcMs = 10_000L)
        val payload = session.toPayload(endedUtcMs = 70_000L)
        assertEquals(60L, payload.durationSec)
        assertEquals(0, payload.hrSampleCount)
        assertNull(payload.avgBpm)
        assertNull(payload.maxBpm)
    }

    @Test
    fun `finished session accumulates heart-rate statistics`() {
        val session = ActiveWatchWorkout(id = "w-3", startedUtcMs = 0L)
            .withHeartRateSample(100.0)
            .withHeartRateSample(140.0)
            .withHeartRateSample(120.0)
        val payload = session.toPayload(endedUtcMs = 3_000L)
        assertEquals(3L, payload.durationSec)
        assertEquals(3, payload.hrSampleCount)
        assertEquals(120.0, payload.avgBpm!!, 0.0001)
        assertEquals(140.0, payload.maxBpm!!, 0.0001)
    }
}
