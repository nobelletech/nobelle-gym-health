package com.nobelle.phone.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nobelle.core.model.OwnerId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists only the stable OwnerId (issuer + subject) and the "Drive consent
 * granted" marker on local storage, encrypted with an AES/GCM key held in
 * AndroidKeyStore. Tokens are never written here — the marker records only
 * that the user once completed the Drive consent screen, so background sync
 * knows a silent token refresh via Play Services is even worth attempting.
 *
 * The key provider/remover are injectable (internal constructor) so the file
 * format and consent semantics are covered by JVM unit tests; production
 * always uses the AndroidKeyStore default.
 */
class EncryptedOwnerStore internal constructor(
    filesDir: File,
    private val keyProvider: () -> SecretKey,
    private val keyRemover: () -> Unit,
) {
    constructor(context: Context) : this(
        filesDir = context.noBackupFilesDir,
        keyProvider = ::androidKeyStoreKey,
        keyRemover = ::deleteAndroidKeyStoreKey,
    )

    private val file = File(filesDir, FILE_NAME)
    private val tempFile = File(filesDir, "$FILE_NAME.tmp")
    private val consentFile = File(filesDir, CONSENT_FILE_NAME)
    private val consentTempFile = File(filesDir, "$CONSENT_FILE_NAME.tmp")
    private val emailFile = File(filesDir, EMAIL_FILE_NAME)
    private val emailTempFile = File(filesDir, "$EMAIL_FILE_NAME.tmp")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): OwnerId? {
        val plaintext = readDecrypted(file) ?: return null
        return runCatching {
            json.decodeFromString(OwnerId.serializer(), plaintext.decodeToString())
        }.getOrNull()
    }

    /**
     * Records the owner identity encrypted. Establishing an identity always
     * requires a fresh Drive grant afterwards, so any previously recorded
     * Drive consent marker is dropped first — a write failure cannot leave
     * consent bound to a stale owner.
     */
    fun write(ownerId: OwnerId) {
        setDriveConsentGranted(false)
        writeEncrypted(file, tempFile, json.encodeToString(ownerId).encodeToByteArray())
    }

    /**
     * Records that the user completed the Drive app-data consent — never a
     * token, never an expiry. [accountEmail] is the Google account email the
     * consent ran under, needed later to build the local `Account` handle for
     * a silent token refresh; it is stored encrypted, is never part of the
     * owner identity and is never logged or sent anywhere.
     *
     * The email file is written before the marker so a partial write degrades
     * to "granted but no account — silent refresh stays inert", not the
     * reverse.
     */
    fun setDriveConsentGranted(granted: Boolean, accountEmail: String? = null) {
        if (granted) {
            if (accountEmail.isNullOrBlank()) {
                emailTempFile.delete()
                emailFile.delete()
            } else {
                writeEncrypted(emailFile, emailTempFile, accountEmail.encodeToByteArray())
            }
            writeEncrypted(consentFile, consentTempFile, CONSENT_MARKER)
        } else {
            consentTempFile.delete()
            consentFile.delete()
            emailTempFile.delete()
            emailFile.delete()
        }
    }

    /** True only while the consent marker file decrypts to the expected value. */
    fun isDriveConsentGranted(): Boolean =
        readDecrypted(consentFile)?.contentEquals(CONSENT_MARKER) == true

    /**
     * The Google account email recorded at consent time, or null when consent
     * predates email capture (or was given without one). Used only by the
     * background worker to mint a local Drive token — never an identity.
     */
    fun driveAccountEmail(): String? =
        readDecrypted(emailFile)?.decodeToString()?.takeIf { it.isNotBlank() }

    /**
     * Removes the local identity file, the consent marker, the account email
     * and the encryption key. Not used by Drive disconnect — disconnect only
     * drops the marker.
     */
    fun clear() {
        file.delete()
        tempFile.delete()
        consentFile.delete()
        consentTempFile.delete()
        emailFile.delete()
        emailTempFile.delete()
        keyRemover()
    }

    private fun readDecrypted(target: File): ByteArray? {
        val bytes = runCatching { target.readBytes() }.getOrNull() ?: return null
        return runCatching { decrypt(bytes) }.getOrNull()
    }

    private fun writeEncrypted(target: File, temp: File, plaintext: ByteArray) {
        // AndroidKeyStore requires randomized GCM encryption: the IV must come
        // from the cipher, a caller-supplied IV is rejected on-device.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected keystore GCM IV length" }
        val encoded = ByteBuffer.allocate(1 + 1 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        temp.writeBytes(encoded)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            temp.delete()
            throw IOException("Could not persist owner identity", failure)
        }
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.remaining() >= 2 && buffer.get() == VERSION) { "unsupported owner identity format" }
        val ivLength = buffer.get().toInt()
        require(ivLength == IV_LENGTH && buffer.remaining() > ivLength) { "malformed owner identity" }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val FILE_NAME = "owner_identity.v1"
        const val CONSENT_FILE_NAME = "drive_consent.v1"
        const val EMAIL_FILE_NAME = "drive_consent_email.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
        const val VERSION: Byte = 1
        val CONSENT_MARKER = "1".encodeToByteArray()
    }
}

/** AES/GCM key in AndroidKeyStore shared by the identity and consent files. */
private fun androidKeyStoreKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
    )
    return generator.generateKey()
}

private fun deleteAndroidKeyStoreKey() {
    runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "nobelle_owner_identity_v1"
private const val KEY_SIZE_BITS = 256
