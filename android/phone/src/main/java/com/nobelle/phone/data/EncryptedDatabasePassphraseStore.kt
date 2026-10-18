package com.nobelle.phone.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the random 32-byte SQLCipher passphrase with an AES/GCM key held in
 * AndroidKeyStore and stores only the ciphertext in noBackupFilesDir. The
 * passphrase never exists as a String and is never logged.
 */
class EncryptedDatabasePassphraseStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")

    /**
     * Returns the 32-byte passphrase. Fails closed: an existing database file
     * without its key file means the data is unreadable — generating a fresh
     * key would silently strand it, so this throws instead.
     */
    fun getOrCreate(databaseFileExists: Boolean): ByteArray {
        if (file.isFile) {
            val decrypted = decrypt(file.readBytes())
            require(decrypted.size == PASSPHRASE_LENGTH) { "Stored database passphrase is malformed" }
            return decrypted.copyOf()
        }
        check(!databaseFileExists) {
            "Encrypted database exists but its passphrase is missing; refusing to overwrite access"
        }
        val passphrase = ByteArray(PASSPHRASE_LENGTH).also(SecureRandom()::nextBytes)
        writeAtomically(encrypt(passphrase))
        val reread = decrypt(file.readBytes())
        require(reread.size == PASSPHRASE_LENGTH) { "Stored database passphrase is malformed" }
        return reread
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        // AndroidKeyStore requires randomized GCM encryption: the IV must come
        // from the cipher, a caller-supplied IV is rejected on-device.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected keystore GCM IV length" }
        return ByteBuffer.allocate(1 + 1 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.remaining() >= 2 && buffer.get() == VERSION) { "Unsupported database key format" }
        val ivLength = buffer.get().toInt()
        require(ivLength == IV_LENGTH && buffer.remaining() > ivLength) { "Malformed database key file" }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun writeAtomically(bytes: ByteArray) {
        tempFile.writeBytes(bytes)
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            tempFile.delete()
            throw IOException("Could not persist database passphrase", failure)
        }
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
        const val FILE_NAME = "database_key.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nobelle_database_key_wrap_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
        const val PASSPHRASE_LENGTH = 32
        const val VERSION: Byte = 1
    }
}
