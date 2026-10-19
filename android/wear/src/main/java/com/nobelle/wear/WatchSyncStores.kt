package com.nobelle.wear

import android.content.Context
import com.nobelle.core.model.ActiveWatchWorkout
import com.nobelle.core.model.WatchSyncCodec
import com.nobelle.core.model.WatchSyncEventBody
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/**
 * The single in-progress workout, persisted wrapped in AES/GCM under
 * `watch_workout_active.v1` so an ACTIVE session survives process death and
 * the elapsed timer resumes on attach. Contains only the start instant and
 * heart-rate accumulators — never raw samples.
 */
class WatchActiveWorkoutStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): ActiveWatchWorkout? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(
                ActiveWatchWorkout.serializer(),
                EnrollKeystoreCipher.decrypt(KEY_ALIAS, bytes).decodeToString(),
            )
        }.getOrNull()
    }

    fun write(session: ActiveWatchWorkout) {
        val plaintext =
            json.encodeToString(ActiveWatchWorkout.serializer(), session)
                .encodeToByteArray()
        writeAtomically(EnrollKeystoreCipher.encrypt(KEY_ALIAS, plaintext))
    }

    fun clear() {
        file.delete()
        tempFile.delete()
    }

    private fun writeAtomically(bytes: ByteArray) {
        tempFile.writeBytes(bytes)
        try {
            Files.move(
                tempFile.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            tempFile.delete()
            throw IOException("Could not persist active workout", failure)
        }
    }

    private companion object {
        const val FILE_NAME = "watch_workout_active.v1"
        const val KEY_ALIAS = "nobelle_watch_active_workout_v1"
    }
}

/**
 * Watch-side send queue: one wrapped file per operation under
 * `noBackupFilesDir/watch_outbox/`. Each file holds the canonical
 * [WatchSyncEventBody] bytes — UNSIGNED. The HMAC tag is applied at send time
 * with whatever enrollment token is current, so a workout queued before a
 * re-enrollment is still deliverable afterwards. A file leaves the queue only
 * when the phone ACKs its operationUuid.
 */
class WatchOutboxStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, DIR_NAME)

    data class Queued(val operationUuid: String, val bodyBytes: ByteArray)

    fun enqueue(body: WatchSyncEventBody) {
        dir.mkdirs()
        val file = fileFor(body.operationUuid)
        val wrapped = EnrollKeystoreCipher.encrypt(
            KEY_ALIAS, WatchSyncCodec.encodeBody(body),
        )
        val temp = File(dir, file.name + ".tmp")
        temp.writeBytes(wrapped)
        try {
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            temp.delete()
            throw IOException("Could not queue workout for the phone", failure)
        }
    }

    /** operationUuid -> plaintext canonical body. Undecryptable files are
     *  skipped; callers delete them since they can never be delivered. */
    fun list(): List<Queued> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?: return emptyList()
        return files.mapNotNull { file ->
            val operationUuid = file.name.removeSuffix(SUFFIX)
            val bytes = runCatching {
                EnrollKeystoreCipher.decrypt(KEY_ALIAS, file.readBytes())
            }.getOrNull() ?: return@mapNotNull null
            Queued(operationUuid, bytes)
        }
    }

    fun delete(operationUuid: String) {
        fileFor(operationUuid).delete()
        File(dir, "$operationUuid$SUFFIX.tmp").delete()
    }

    fun count(): Int =
        dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }?.size ?: 0

    private fun fileFor(operationUuid: String): File {
        // UUIDs only — defensively strip separators anyway.
        val safe = operationUuid.filter { it.isLetterOrDigit() || it == '-' }
        return File(dir, "$safe$SUFFIX")
    }

    private companion object {
        const val DIR_NAME = "watch_outbox"
        const val SUFFIX = ".v1"
        const val KEY_ALIAS = "nobelle_watch_outbox_v1"
    }
}
