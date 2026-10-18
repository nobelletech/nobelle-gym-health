package com.nobelle.phone.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabasePassphraseStoreTest {
    private lateinit var context: Context
    private lateinit var keyFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyFile = File(context.noBackupFilesDir, "database_key.v1")
        keyFile.delete()
        File(context.noBackupFilesDir, "database_key.v1.tmp").delete()
    }

    @Test
    fun generatedPassphraseIsThirtyTwoBytesAndStableAcrossReads() {
        val first = EncryptedDatabasePassphraseStore(context).getOrCreate(databaseFileExists = false)
        assertEquals(32, first.size)
        assertTrue(first.any { it != 0.toByte() })

        val reread = EncryptedDatabasePassphraseStore(context).getOrCreate(databaseFileExists = true)
        assertTrue(first.contentEquals(reread))
    }

    @Test
    fun storedCiphertextDoesNotContainThePassphrase() {
        val passphrase = EncryptedDatabasePassphraseStore(context).getOrCreate(databaseFileExists = false)
        val ciphertext = keyFile.readBytes()
        assertFalse(ciphertext.contentEquals(passphrase))
        assertFalse(containsSubsequence(ciphertext, passphrase))
    }

    @Test
    fun missingKeyFileWithExistingDatabaseFailsClosed() {
        try {
            EncryptedDatabasePassphraseStore(context).getOrCreate(databaseFileExists = true)
            fail("Existing database without a passphrase must not silently generate a new key")
        } catch (expected: IllegalStateException) {
        }
        assertFalse(keyFile.exists()) // fail closed: nothing new written
    }

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
