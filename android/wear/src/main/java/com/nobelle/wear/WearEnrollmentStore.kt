package com.nobelle.wear

import android.content.Context
import com.nobelle.core.model.WatchPhoneEnrollment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.json.Json

/**
 * The watch's persistent installation identifier — a random UUID in a plain
 * file in noBackupFilesDir. It is an identifier, not a secret, and never
 * carries account data.
 */
class WearInstallationId(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun getOrCreate(): String =
        if (file.isFile) {
            file.readText().trim()
        } else {
            UUID.randomUUID().toString().also { file.writeText(it) }
        }

    private companion object {
        const val FILE_NAME = "watch_installation_id.v1"
    }
}

/**
 * The watch's enrollment record ({phoneInstallationId, token, enrolledUtc}),
 * wrapped with AES/GCM in AndroidKeyStore. The token is never written in
 * plaintext, displayed, or logged.
 */
class WearEnrollmentStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): WatchPhoneEnrollment? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(
                WatchPhoneEnrollment.serializer(),
                EnrollKeystoreCipher.decrypt(KEY_ALIAS, bytes).decodeToString(),
            )
        }.getOrNull()
    }

    fun write(enrollment: WatchPhoneEnrollment) {
        val plaintext =
            json.encodeToString(WatchPhoneEnrollment.serializer(), enrollment)
                .encodeToByteArray()
        tempFile.writeBytes(EnrollKeystoreCipher.encrypt(KEY_ALIAS, plaintext))
        try {
            Files.move(
                tempFile.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            tempFile.delete()
            throw IOException("Could not persist phone enrollment", failure)
        }
    }

    fun clear() {
        file.delete()
        tempFile.delete()
    }

    private companion object {
        const val FILE_NAME = "phone_enrollment.v1"
        const val KEY_ALIAS = "nobelle_phone_enrollment_v1"
    }
}
