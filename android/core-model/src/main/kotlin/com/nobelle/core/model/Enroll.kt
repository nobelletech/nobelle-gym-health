package com.nobelle.core.model

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phone <-> watch enrollment over the Wear OS Data Layer (§7/§24).
 *
 * Enrollment is a one-time authenticated key agreement — it is NOT sync and
 * transfers no health data. Both sides display the same 6-digit code derived
 * from the watch's ephemeral public key and nonce so the user can confirm the
 * two devices are talking to each other. The resulting 32-byte enrollment
 * token (ECDH + HKDF-SHA256) is never displayed, logged, or stored in
 * plaintext; each side keeps it inside an Android-Keystore-wrapped file.
 *
 * Wire paths share [EnrollPaths.PREFIX]; payloads are kotlinx-serialization
 * JSON at [EnrollPaths.SCHEMA_VERSION].
 */
object EnrollPaths {
    const val PREFIX = "/nobelle/enroll/v1/"
    const val REQUEST = PREFIX + "request"
    const val RESPONSE = PREFIX + "response"
    const val REVOKE = PREFIX + "revoke"
    const val SCHEMA_VERSION = 1
}

/** Watch -> phone: ask to enroll. Pubkey and nonce are base64. */
@Serializable
data class EnrollRequest(
    val schemaVersion: Int = EnrollPaths.SCHEMA_VERSION,
    val watchInstallationId: String,
    val watchModel: String,
    val watchPubKeyX962: String,
    val nonce: String,
)

/** Phone -> watch: answer a request. Rejections carry no key material. */
@Serializable
data class EnrollResponse(
    val schemaVersion: Int = EnrollPaths.SCHEMA_VERSION,
    val phoneInstallationId: String = "",
    val phonePubKeyX962: String = "",
    val nonce: String = "",
    val accepted: Boolean,
)

/**
 * Either side -> the other: enrollment is revoked. [installationId] is the
 * SENDER's installation id so the receiver can verify the message concerns
 * its own enrollment before deleting anything.
 */
@Serializable
data class EnrollRevoke(
    val schemaVersion: Int = EnrollPaths.SCHEMA_VERSION,
    val installationId: String,
)

/** A request the phone persisted while it waits for the user to confirm. */
@Serializable
data class PendingWatchEnrollRequest(
    val watchInstallationId: String,
    val watchModel: String,
    val watchPubKeyX962: String,
    val nonce: String,
    val sourceNodeId: String,
    val receivedUtc: Instant,
)

/** What the phone persists, owner-scoped, wrapped in AES/GCM.
 *  [eventsReceived] counts authenticated sync events relayed into the
 *  outbox; [lastContactUtc] tracks the last accepted watch message. */
@Serializable
data class PhoneWatchEnrollment(
    val watchInstallationId: String,
    val watchModel: String,
    val tokenBase64: String,
    val enrolledUtc: Instant,
    val lastContactUtc: Instant,
    val eventsReceived: Int = 0,
)

/** What the watch persists, wrapped in AES/GCM. [phoneNodeId] remembers the
 *  Data Layer node that delivered the enrollment response so sync events can
 *  prefer it when still connected; it is a routing hint, not a secret. */
@Serializable
data class WatchPhoneEnrollment(
    val phoneInstallationId: String,
    val tokenBase64: String,
    val enrolledUtc: Instant,
    val phoneNodeId: String = "",
)

/** Strict-but-forgiving wire codec: unknown keys ignored for forward compat. */
object EnrollWire {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeRequest(request: EnrollRequest): ByteArray =
        json.encodeToString(EnrollRequest.serializer(), request).encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): EnrollRequest =
        json.decodeFromString(EnrollRequest.serializer(), bytes.decodeToString())

    fun encodeResponse(response: EnrollResponse): ByteArray =
        json.encodeToString(EnrollResponse.serializer(), response).encodeToByteArray()

    fun decodeResponse(bytes: ByteArray): EnrollResponse =
        json.decodeFromString(EnrollResponse.serializer(), bytes.decodeToString())

    fun encodeRevoke(revoke: EnrollRevoke): ByteArray =
        json.encodeToString(EnrollRevoke.serializer(), revoke).encodeToByteArray()

    fun decodeRevoke(bytes: ByteArray): EnrollRevoke =
        json.decodeFromString(EnrollRevoke.serializer(), bytes.decodeToString())
}

/**
 * All cryptography for enrollment. Pure java.security/javax.crypto so the
 * identical code runs and is unit-tested on both devices without Android.
 */
object EnrollCrypto {
    const val NONCE_LENGTH = 32
    const val TOKEN_LENGTH = 32
    const val KEY_AGREEMENT = "ECDH"
    const val CURVE = "secp256r1"
    private const val HKDF_INFO = "nobelle-enroll-v1"
    private const val CODE_MODULO = 1_000_000L
    private const val X962_UNCOMPRESSED_TAG: Byte = 0x04
    private const val X962_COORDINATE_LENGTH = 32

    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec(CURVE), random)
            generateKeyPair()
        }

    fun generateNonce(): ByteArray = ByteArray(NONCE_LENGTH).also(random::nextBytes)

    /** Uncompressed X9.62 point encoding: 0x04 || X || Y, 65 bytes. */
    fun encodePubKeyX962(key: PublicKey): ByteArray {
        require(key is ECPublicKey) { "Enrollment requires an EC public key" }
        val x = key.w.affineX.toFixed()
        val y = key.w.affineY.toFixed()
        return ByteArray(1 + x.size + y.size).apply {
            this[0] = X962_UNCOMPRESSED_TAG
            x.copyInto(this, 1)
            y.copyInto(this, 1 + x.size)
        }
    }

    fun decodePubKeyX962(encoded: ByteArray): PublicKey {
        require(
            encoded.size == 1 + 2 * X962_COORDINATE_LENGTH &&
                encoded[0] == X962_UNCOMPRESSED_TAG,
        ) { "Malformed X9.62 public key" }
        val params = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec(CURVE))
            getParameterSpec(ECParameterSpec::class.java)
        }
        val point = ECPoint(
            BigInteger(1, encoded.copyOfRange(1, 1 + X962_COORDINATE_LENGTH)),
            BigInteger(1, encoded.copyOfRange(1 + X962_COORDINATE_LENGTH, encoded.size)),
        )
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }

    /** Raw ECDH shared secret between my private key and the peer's X9.62 key. */
    fun ecdhSharedSecret(myPrivate: PrivateKey, peerPubKeyX962: ByteArray): ByteArray =
        KeyAgreement.getInstance(KEY_AGREEMENT).run {
            init(myPrivate)
            doPhase(decodePubKeyX962(peerPubKeyX962), true)
            generateSecret()
        }

    /**
     * enrollmentToken = HKDF-SHA256(
     *   ikm  = ECDH(myPriv, theirPub),
     *   salt = watchNonce || phoneNonce,
     *   info = "nobelle-enroll-v1",
     *   L    = 32,
     * )
     * Both sides derive the identical token independently.
     */
    fun deriveEnrollmentToken(
        myPrivate: PrivateKey,
        peerPubKeyX962: ByteArray,
        watchNonce: ByteArray,
        phoneNonce: ByteArray,
    ): ByteArray {
        val ikm = ecdhSharedSecret(myPrivate, peerPubKeyX962)
        return hkdfSha256(
            ikm = ikm,
            salt = watchNonce + phoneNonce,
            info = HKDF_INFO.encodeToByteArray(),
            length = TOKEN_LENGTH,
        )
    }

    /** RFC 5869 HKDF with SHA-256: extract then expand. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LENGTH)) { "Invalid HKDF output length" }
        val hmac = Mac.getInstance("HmacSHA256")
        // Extract — RFC 5869: a zero-length salt means HashLen zero bytes.
        val effectiveSalt =
            if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        hmac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)
        // Expand.
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var block = 1
        while (offset < length) {
            hmac.reset()
            hmac.update(previous)
            hmac.update(info)
            hmac.update(byteArrayOf(block.toByte()))
            previous = hmac.doFinal()
            val take = minOf(HASH_LENGTH, length - offset)
            previous.copyInto(out, offset, 0, take)
            offset += take
            block++
        }
        return out
    }

    /**
     * The 6-digit code the user compares on both screens: the decimal value of
     * SHA-256(watchPubKeyX962 || watchNonce) modulo 1,000,000, zero-padded.
     * Either side can compute it from the wire request; it proves the phone
     * received the exact key the watch generated.
     */
    fun confirmationCode(watchPubKeyX962: ByteArray, watchNonce: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(watchPubKeyX962 + watchNonce)
        val value = BigInteger(1, digest).mod(BigInteger.valueOf(CODE_MODULO))
        return "%06d".format(value.toLong())
    }

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun fromBase64(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun BigInteger.toFixed(): ByteArray {
        val raw = toByteArray().let {
            // BigInteger may emit a leading zero sign byte or pad short.
            if (it.size > X962_COORDINATE_LENGTH) {
                it.copyOfRange(it.size - X962_COORDINATE_LENGTH, it.size)
            } else {
                it
            }
        }
        return ByteArray(X962_COORDINATE_LENGTH).also { out ->
            raw.copyInto(out, X962_COORDINATE_LENGTH - raw.size)
        }
    }

    private const val HASH_LENGTH = 32
}

/** Watch-side enrollment state — the only states the watch UI may show. */
sealed interface WatchEnrollState {
    data object NotEnrolled : WatchEnrollState
    data class Waiting(val code: String, val deadlineEpochMs: Long) : WatchEnrollState
    data class Enrolled(val enrolledUtc: Instant) : WatchEnrollState
}

/**
 * Pure watch-side transitions so unit tests can drive request -> waiting ->
 * enrolled, reject -> not-enrolled, timeout -> not-enrolled and revoke.
 */
object WatchEnrollTransitions {
    const val REQUEST_TIMEOUT_MS = 60_000L

    /** The user tapped "Enroll watch" and the request was sent. */
    fun requestSent(code: String, nowEpochMs: Long): WatchEnrollState =
        WatchEnrollState.Waiting(code, nowEpochMs + REQUEST_TIMEOUT_MS)

    /** A RESPONSE arrived while waiting. Rejection returns to not-enrolled. */
    fun responseReceived(
        state: WatchEnrollState,
        response: EnrollResponse,
        enrolledUtc: Instant,
    ): WatchEnrollState =
        when {
            state !is WatchEnrollState.Waiting -> state
            !response.accepted -> WatchEnrollState.NotEnrolled
            else -> WatchEnrollState.Enrolled(enrolledUtc)
        }

    /** Clock check: a waiting request past its deadline expires honestly. */
    fun tick(state: WatchEnrollState, nowEpochMs: Long): WatchEnrollState =
        if (state is WatchEnrollState.Waiting && nowEpochMs >= state.deadlineEpochMs) {
            WatchEnrollState.NotEnrolled
        } else {
            state
        }

    /** The user cancelled, or the phone revoked: local state clears. */
    fun cleared(): WatchEnrollState = WatchEnrollState.NotEnrolled
}
