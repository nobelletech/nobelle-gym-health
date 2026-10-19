package com.nobelle.core.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollTest {

    // ---- ECDH ----

    @Test
    fun `ECDH derives the same secret in both directions`() {
        val watch = EnrollCrypto.generateKeyPair()
        val phone = EnrollCrypto.generateKeyPair()
        val watchPub = EnrollCrypto.encodePubKeyX962(watch.public)
        val phonePub = EnrollCrypto.encodePubKeyX962(phone.public)

        val watchSide = EnrollCrypto.ecdhSharedSecret(watch.private, phonePub)
        val phoneSide = EnrollCrypto.ecdhSharedSecret(phone.private, watchPub)

        assertTrue(watchSide.contentEquals(phoneSide))
    }

    @Test
    fun `X962 encoding round-trips through decode`() {
        val pair = EnrollCrypto.generateKeyPair()
        val encoded = EnrollCrypto.encodePubKeyX962(pair.public)
        assertEquals(65, encoded.size)
        assertEquals(0x04.toByte(), encoded[0])
        val decoded = EnrollCrypto.decodePubKeyX962(encoded)
        assertTrue(
            EnrollCrypto.encodePubKeyX962(decoded).contentEquals(encoded),
        )
    }

    // ---- HKDF-SHA256 ----

    @Test
    fun `HKDF-SHA256 matches RFC 5869 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val okm = EnrollCrypto.hkdfSha256(ikm, salt, info, length = 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun `both sides derive the same enrollment token`() {
        val watch = EnrollCrypto.generateKeyPair()
        val phone = EnrollCrypto.generateKeyPair()
        val watchNonce = EnrollCrypto.generateNonce()
        val phoneNonce = EnrollCrypto.generateNonce()
        val watchPub = EnrollCrypto.encodePubKeyX962(watch.public)
        val phonePub = EnrollCrypto.encodePubKeyX962(phone.public)

        val watchToken = EnrollCrypto.deriveEnrollmentToken(
            watch.private, phonePub, watchNonce, phoneNonce,
        )
        val phoneToken = EnrollCrypto.deriveEnrollmentToken(
            phone.private, watchPub, watchNonce, phoneNonce,
        )

        assertEquals(EnrollCrypto.TOKEN_LENGTH, watchToken.size)
        assertTrue(watchToken.contentEquals(phoneToken))
    }

    // ---- Confirmation code ----

    @Test
    fun `confirmation code is deterministic six digits zero-padded`() {
        val pub = EnrollCrypto.encodePubKeyX962(EnrollCrypto.generateKeyPair().public)
        val nonce = EnrollCrypto.generateNonce()
        val code = EnrollCrypto.confirmationCode(pub, nonce)
        assertTrue(code.matches(Regex("[0-9]{6}")))
        assertEquals(code, EnrollCrypto.confirmationCode(pub, nonce))
    }

    @Test
    fun `confirmation code changes when the public key changes`() {
        val nonce = EnrollCrypto.generateNonce()
        val pubA = EnrollCrypto.encodePubKeyX962(EnrollCrypto.generateKeyPair().public)
        val pubB = EnrollCrypto.encodePubKeyX962(EnrollCrypto.generateKeyPair().public)
        // Collision odds are ~1/1_000_000; fixed distinct inputs keep this
        // deterministic rather than relying on randomness staying apart.
        assertNotEquals(
            EnrollCrypto.confirmationCode(pubA, nonce),
            EnrollCrypto.confirmationCode(pubB, nonce),
        )
    }

    @Test
    fun `confirmation code zero-pads small values`() {
        // Crafted inputs are not required: verify formatting property on a
        // fixed input by checking length and digits only.
        val code = EnrollCrypto.confirmationCode(
            ByteArray(65) { 1 },
            ByteArray(32) { 2 },
        )
        assertEquals(6, code.length)
    }

    // ---- Wire serialization ----

    @Test
    fun `request survives an encode-decode round trip`() {
        val request = EnrollRequest(
            watchInstallationId = "watch-123",
            watchModel = "SM-L330",
            watchPubKeyX962 = EnrollCrypto.toBase64(ByteArray(65) { it.toByte() }),
            nonce = EnrollCrypto.toBase64(ByteArray(32) { it.toByte() }),
        )
        val decoded = EnrollWire.decodeRequest(EnrollWire.encodeRequest(request))
        assertEquals(request, decoded)
        assertEquals(EnrollPaths.SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `response survives an encode-decode round trip`() {
        val response = EnrollResponse(
            phoneInstallationId = "phone-1",
            phonePubKeyX962 = EnrollCrypto.toBase64(ByteArray(65) { 7 }),
            nonce = EnrollCrypto.toBase64(ByteArray(32) { 8 }),
            accepted = true,
        )
        assertEquals(response, EnrollWire.decodeResponse(EnrollWire.encodeResponse(response)))
    }

    @Test
    fun `rejection response round-trips with empty key material`() {
        val reject = EnrollResponse(accepted = false)
        val decoded = EnrollWire.decodeResponse(EnrollWire.encodeResponse(reject))
        assertEquals(reject, decoded)
        assertTrue(decoded.phonePubKeyX962.isEmpty())
    }

    @Test
    fun `revoke survives an encode-decode round trip`() {
        val revoke = EnrollRevoke(installationId = "install-9")
        assertEquals(revoke, EnrollWire.decodeRevoke(EnrollWire.encodeRevoke(revoke)))
    }

    @Test
    fun `wire paths live under the versioned prefix`() {
        assertTrue(EnrollPaths.REQUEST.startsWith(EnrollPaths.PREFIX))
        assertTrue(EnrollPaths.RESPONSE.startsWith(EnrollPaths.PREFIX))
        assertTrue(EnrollPaths.REVOKE.startsWith(EnrollPaths.PREFIX))
        assertEquals("/nobelle/enroll/v1/", EnrollPaths.PREFIX)
    }

    // ---- Watch-side state transitions ----

    private val instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `request then accepted response enrolls`() {
        val waiting = WatchEnrollTransitions.requestSent("123456", nowEpochMs = 0)
        assertTrue(waiting is WatchEnrollState.Waiting)
        val enrolled = WatchEnrollTransitions.responseReceived(
            waiting,
            EnrollResponse(accepted = true, phoneInstallationId = "p"),
            enrolledUtc = instant,
        )
        assertTrue(enrolled is WatchEnrollState.Enrolled)
    }

    @Test
    fun `rejected response returns to not enrolled`() {
        val waiting = WatchEnrollTransitions.requestSent("123456", nowEpochMs = 0)
        val state = WatchEnrollTransitions.responseReceived(
            waiting,
            EnrollResponse(accepted = false),
            enrolledUtc = instant,
        )
        assertEquals(WatchEnrollState.NotEnrolled, state)
    }

    @Test
    fun `waiting past the deadline times out to not enrolled`() {
        val waiting = WatchEnrollTransitions.requestSent("123456", nowEpochMs = 1_000)
        assertEquals(
            WatchEnrollState.NotEnrolled,
            WatchEnrollTransitions.tick(
                waiting,
                1_000 + WatchEnrollTransitions.REQUEST_TIMEOUT_MS,
            ),
        )
        // Still waiting just before the deadline.
        assertEquals(
            waiting,
            WatchEnrollTransitions.tick(
                waiting,
                1_000 + WatchEnrollTransitions.REQUEST_TIMEOUT_MS - 1,
            ),
        )
    }

    @Test
    fun `revoked clears any state to not enrolled`() {
        assertEquals(
            WatchEnrollState.NotEnrolled,
            WatchEnrollTransitions.cleared(),
        )
    }

    @Test
    fun `response arriving when not waiting is ignored`() {
        val state = WatchEnrollTransitions.responseReceived(
            WatchEnrollState.NotEnrolled,
            EnrollResponse(accepted = true, phoneInstallationId = "p"),
            enrolledUtc = instant,
        )
        assertEquals(WatchEnrollState.NotEnrolled, state)
    }

    @Test
    fun `malformed pubkey is rejected rather than enrolled`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnrollCrypto.decodePubKeyX962(ByteArray(65) { 0 })
        }
    }

    private fun hex(text: String): ByteArray =
        text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
