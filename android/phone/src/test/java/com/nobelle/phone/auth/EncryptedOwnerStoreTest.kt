package com.nobelle.phone.auth

import com.nobelle.core.model.OwnerId
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM coverage of the encrypted store via the internal constructor: a plain
 * AES key stands in for AndroidKeyStore (javax.crypto runs on the JDK), so
 * the file format and the consent-marker semantics are testable without a
 * device.
 */
class EncryptedOwnerStoreTest {
    private lateinit var dir: File
    private lateinit var key: SecretKey
    private var keyRemoved = false

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("owner-store-test").toFile()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keyRemoved = false
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store() = EncryptedOwnerStore(
        filesDir = dir,
        keyProvider = { key },
        keyRemover = { keyRemoved = true },
    )

    private val owner = OwnerId(issuer = "https://accounts.google.com", subject = "abc123")

    @Test
    fun `owner identity round-trips across store instances`() {
        store().write(owner)
        assertEquals(owner, store().read())
    }

    @Test
    fun `consent marker defaults to not granted`() {
        assertFalse(store().isDriveConsentGranted())
    }

    @Test
    fun `consent marker persists across store instances`() {
        store().setDriveConsentGranted(true)
        assertTrue(store().isDriveConsentGranted())
        store().setDriveConsentGranted(false)
        assertFalse(store().isDriveConsentGranted())
    }

    @Test
    fun `consent records the account email across store instances`() {
        store().setDriveConsentGranted(true, "owner@example.com")
        val again = store()
        assertTrue(again.isDriveConsentGranted())
        assertEquals("owner@example.com", again.driveAccountEmail())
    }

    @Test
    fun `consent without an email drops a previously recorded one`() {
        val store = store()
        store.setDriveConsentGranted(true, "owner@example.com")
        store.setDriveConsentGranted(true, null)
        assertTrue(store.isDriveConsentGranted())
        assertNull(store.driveAccountEmail())
    }

    @Test
    fun `no consent means no recorded email`() {
        assertNull(store().driveAccountEmail())
    }

    @Test
    fun `writing an owner resets any recorded Drive consent`() {
        val store = store()
        store.setDriveConsentGranted(true, "owner@example.com")
        store.write(owner)
        assertFalse(store.isDriveConsentGranted())
        assertNull(store.driveAccountEmail())
        assertEquals(owner, store.read())
    }

    @Test
    fun `clear removes identity consent and email and drops the key`() {
        val store = store()
        store.write(owner)
        store.setDriveConsentGranted(true, "owner@example.com")
        store.clear()
        assertNull(store().read())
        assertFalse(store().isDriveConsentGranted())
        assertNull(store().driveAccountEmail())
        assertTrue(keyRemoved)
    }

    @Test
    fun `marker file alone does not grant consent when it cannot decrypt`() {
        // A leftover file that no longer decrypts must not count as consent.
        File(dir, "drive_consent.v1").writeBytes(byteArrayOf(9, 9, 9))
        assertFalse(store().isDriveConsentGranted())
    }
}
