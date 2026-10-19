package com.nobelle.phone.wear

import android.content.Context
import com.nobelle.core.model.PendingWatchEnrollRequest
import com.nobelle.core.model.PhoneWatchEnrollment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * Owner-scoped, AES/GCM-Keystore-wrapped enrollment record for the phone side.
 * One enrollment exists per owner identity: the file name is a keyed hash of
 * the owner uid so no account data appears on disk. The token inside never
 * exists in plaintext outside this wrapper.
 */
class WatchEnrollmentStore(context: Context) {
    private val dir = context.noBackupFilesDir
    private val json = Json { ignoreUnknownKeys = true }

    fun read(ownerUid: String): PhoneWatchEnrollment? {
        val file = fileFor(ownerUid)
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(
                PhoneWatchEnrollment.serializer(),
                EnrollKeystoreCipher.decrypt(KEY_ALIAS, bytes).decodeToString(),
            )
        }.getOrNull()
    }

    fun write(ownerUid: String, enrollment: PhoneWatchEnrollment) {
        val plaintext =
            json.encodeToString(PhoneWatchEnrollment.serializer(), enrollment)
                .encodeToByteArray()
        writeAtomically(fileFor(ownerUid), EnrollKeystoreCipher.encrypt(KEY_ALIAS, plaintext))
    }

    fun clear(ownerUid: String) {
        fileFor(ownerUid).delete()
        tempFor(ownerUid).delete()
    }

    private fun fileFor(ownerUid: String) =
        File(dir, "watch_enrollment.${scope(ownerUid)}.v1")

    private fun tempFor(ownerUid: String) =
        File(dir, "watch_enrollment.${scope(ownerUid)}.v1.tmp")

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        try {
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            temp.delete()
            throw IOException("Could not persist watch enrollment", failure)
        }
    }

    internal companion object {
        const val KEY_ALIAS = "nobelle_watch_enrollment_v1"

        /** Short stable scope token for a filename — never the raw uid. */
        fun scope(ownerUid: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(ownerUid.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }
}

/**
 * Owner-scoped pending request written by the listener service and observed
 * by the Today-screen card. Holds no secrets — the watch public key and nonce
 * are exactly what traveled over the wire — but is wrapped anyway so request
 * files follow the same encrypted-at-rest pattern as everything private.
 */
class PendingWatchRequestStore(context: Context) {
    private val dir = context.noBackupFilesDir
    private val json = Json { ignoreUnknownKeys = true }

    fun read(ownerUid: String): PendingWatchEnrollRequest? {
        val bytes = runCatching { fileFor(ownerUid).readBytes() }.getOrNull()
            ?: return null
        return runCatching {
            json.decodeFromString(
                PendingWatchEnrollRequest.serializer(),
                EnrollKeystoreCipher.decrypt(KEY_ALIAS, bytes).decodeToString(),
            )
        }.getOrNull()
    }

    fun write(ownerUid: String, request: PendingWatchEnrollRequest) {
        val plaintext =
            json.encodeToString(PendingWatchEnrollRequest.serializer(), request)
                .encodeToByteArray()
        val file = fileFor(ownerUid)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(EnrollKeystoreCipher.encrypt(KEY_ALIAS, plaintext))
        try {
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            temp.delete()
            throw IOException("Could not persist watch pairing request", failure)
        }
    }

    fun clear(ownerUid: String) {
        fileFor(ownerUid).delete()
        File(dir, "watch_enroll_pending.${WatchEnrollmentStore.scope(ownerUid)}.v1.tmp")
            .delete()
    }

    private fun fileFor(ownerUid: String) =
        File(dir, "watch_enroll_pending.${WatchEnrollmentStore.scope(ownerUid)}.v1")

    private companion object {
        const val KEY_ALIAS = "nobelle_watch_enroll_pending_v1"
    }
}
