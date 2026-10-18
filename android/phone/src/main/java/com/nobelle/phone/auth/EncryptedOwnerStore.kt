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
 * Persists only the stable OwnerId (issuer + subject) on local storage, encrypted
 * with an AES/GCM key held in AndroidKeyStore. Tokens are never written here.
 */
class EncryptedOwnerStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): OwnerId? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return runCatching { decrypt(bytes) }.getOrNull()
    }

    fun write(ownerId: OwnerId) {
        val plaintext = json.encodeToString(ownerId).encodeToByteArray()
        // AndroidKeyStore requires randomized GCM encryption: the IV must come
        // from the cipher, a caller-supplied IV is rejected on-device.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected keystore GCM IV length" }
        val encoded = ByteBuffer.allocate(1 + 1 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        tempFile.writeBytes(encoded)
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            tempFile.delete()
            throw IOException("Could not persist owner identity", failure)
        }
    }

    /** Removes the local identity file and its encryption key. Not used by Drive disconnect. */
    fun clear() {
        file.delete()
        tempFile.delete()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun decrypt(bytes: ByteArray): OwnerId {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.remaining() >= 2 && buffer.get() == VERSION) { "unsupported owner identity format" }
        val ivLength = buffer.get().toInt()
        require(ivLength == IV_LENGTH && buffer.remaining() > ivLength) { "malformed owner identity" }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext).decodeToString()
        return json.decodeFromString(OwnerId.serializer(), plaintext)
    }

    private fun key(): SecretKey {
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

    private companion object {
        const val FILE_NAME = "owner_identity.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nobelle_owner_identity_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
        const val VERSION: Byte = 1
    }
}
