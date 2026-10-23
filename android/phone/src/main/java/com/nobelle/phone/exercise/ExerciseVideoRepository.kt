package com.nobelle.phone.exercise

import android.content.Context
import com.nobelle.core.coaching.ExerciseCatalog
import com.nobelle.core.coaching.ExerciseMediaManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Offline-first exercise-video store. Every download is driven by the media
 * manifest (url + sha256 + provenance), streams to `<id>.mp4.part` inside
 * `filesDir/exercise_media/`, and is renamed to `<id>.mp4` only after the
 * sha256 matches. Mismatches and interruptions delete the partial file, so no
 * corrupt video is ever kept or played; retrying always restarts clean.
 *
 * Plain coroutines + HttpURLConnection — no new download dependencies.
 * Videos that exist on disk are re-verified against the manifest checksum
 * once at startup and discarded if they no longer match.
 */
class ExerciseVideoRepository(context: Context) {
    private val mediaDir = File(context.filesDir, "exercise_media")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entries by lazy { ExerciseMediaManifest.byId() }

    private val flows: Map<String, MutableStateFlow<VideoDownloadState>> =
        ExerciseCatalog.all().associate { exercise ->
            exercise.id to MutableStateFlow(initialState(exercise.id))
        }

    private val jobs = mutableMapOf<String, Job>()

    // Generation guard: a cancelled job's cleanup must never touch state (or
    // delete the .part file) after a newer download for the same id started.
    private val generations = mutableMapOf<String, Long>()
    private var generationCounter = 0L

    init {
        // Re-verify anything already on disk against the manifest checksum.
        flows.forEach { (id, flow) ->
            val file = videoFile(id)
            if (file.exists() && flow.value is VideoDownloadState.Downloaded) {
                scope.launch {
                    val expected = entries[id]?.video?.sha256
                    if (expected != null && sha256Hex(file).equals(expected, ignoreCase = true)) {
                        flow.update { VideoDownloadState.Downloaded }
                    } else {
                        file.delete()
                        flow.update { VideoDownloadState.NotDownloaded }
                    }
                }
            }
        }
    }

    private fun initialState(exerciseId: String): VideoDownloadState {
        val video = entries[exerciseId]?.video
        return when {
            video?.url.isNullOrBlank() -> VideoDownloadState.NotAvailable
            videoFile(exerciseId).exists() -> VideoDownloadState.Downloaded
            else -> VideoDownloadState.NotDownloaded
        }
    }

    fun state(exerciseId: String): StateFlow<VideoDownloadState> =
        flows[exerciseId] ?: MutableStateFlow(VideoDownloadState.NotAvailable)

    /** The verified local file, or null when not downloaded. */
    fun downloadedFile(exerciseId: String): File? {
        val file = videoFile(exerciseId)
        return file.takeIf { it.exists() }
    }

    fun download(exerciseId: String) {
        val flow = flows[exerciseId] ?: return
        val video = entries[exerciseId]?.video
        val url = video?.url
        if (url.isNullOrBlank()) {
            flow.value = VideoDownloadState.NotAvailable
            return
        }
        val expectedSha = video.sha256
        if (expectedSha.isNullOrBlank()) {
            flow.value = VideoDownloadState.Failed("Manifest is missing the video checksum")
            return
        }
        cancel(exerciseId) // restart clean from any interrupted state
        val generation = ++generationCounter
        generations[exerciseId] = generation
        val job = scope.launch {
            val target = videoFile(exerciseId)
            val part = File(mediaDir, "$exerciseId.mp4.part")
            try {
                mediaDir.mkdirs()
                part.delete()
                flow.update { VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Start) }

                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("Accept", "video/mp4")
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.disconnect()
                    throw java.io.IOException("Server answered HTTP $code")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 }
                connection.inputStream.use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total != null) {
                                val fraction = copied.toFloat() / total
                                flow.update {
                                    VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Progress(fraction))
                                }
                            }
                        }
                    }
                }
                connection.disconnect()

                val actual = sha256Hex(part)
                if (actual.equals(expectedSha, ignoreCase = true)) {
                    if (part.renameTo(target)) {
                        flow.update { VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Complete) }
                    } else {
                        part.delete()
                        flow.update {
                            VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Fail("Could not finalize the download"))
                        }
                    }
                } else {
                    part.delete()
                    flow.update {
                        VideoDownloadStateMachine.reduce(
                            it,
                            VideoDownloadEvent.Fail("Checksum mismatch — downloaded file discarded"),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (generations[exerciseId] == generation) {
                    part.delete()
                    flow.update { VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Cancel) }
                }
                throw cancelled
            } catch (failure: Exception) {
                if (generations[exerciseId] == generation) {
                    part.delete()
                    flow.update {
                        VideoDownloadStateMachine.reduce(
                            it,
                            VideoDownloadEvent.Fail(failure.message ?: "Download interrupted"),
                        )
                    }
                }
            }
        }
        jobs[exerciseId] = job
    }

    /** Cancels an in-flight download; the partial file is deleted and the
     * state returns to NOT_DOWNLOADED (via the machine's Cancel event). */
    fun cancel(exerciseId: String) {
        jobs.remove(exerciseId)?.cancel()
    }

    fun deleteDownloaded(exerciseId: String) {
        cancel(exerciseId)
        videoFile(exerciseId).delete()
        flows[exerciseId]?.update { VideoDownloadStateMachine.reduce(it, VideoDownloadEvent.Reset) }
    }

    private fun videoFile(exerciseId: String): File = File(mediaDir, "$exerciseId.mp4")

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}