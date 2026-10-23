package com.nobelle.phone.exercise

/**
 * Pure reducer for the exercise-video download lifecycle. Kept free of any
 * Android/coroutine types so the whole pipeline is JVM-testable; the
 * repository feeds it real events (start, progress, cancel, checksum verdict)
 * and the UI renders exactly what it returns.
 *
 * States map to the UI as: NOT_AVAILABLE (no url in the manifest — pending
 * content milestone), NOT_DOWNLOADED (url known, nothing on disk yet),
 * DOWNLOADING(progress) (bytes streaming to a .part file), DOWNLOADED
 * (checksum-verified file on disk), FAILED(reason) (interrupted or
 * checksum-mismatch — no corrupt file is ever kept; retry restarts clean).
 */
sealed interface VideoDownloadState {
    data object NotAvailable : VideoDownloadState
    data object NotDownloaded : VideoDownloadState
    data class Downloading(val progress: Float) : VideoDownloadState
    data object Downloaded : VideoDownloadState
    data class Failed(val reason: String) : VideoDownloadState
}

sealed interface VideoDownloadEvent {
    data object Start : VideoDownloadEvent
    data class Progress(val fraction: Float) : VideoDownloadEvent

    /** User cancellation or interrupted download — back to a clean slate. */
    data object Cancel : VideoDownloadEvent

    /** The .part file passed sha256 verification and was renamed. */
    data object Complete : VideoDownloadEvent

    /** Download interrupted or checksum mismatch — [reason] for the UI. */
    data class Fail(val reason: String) : VideoDownloadEvent

    /** A previously downloaded file was deleted. */
    data object Reset : VideoDownloadEvent
}

object VideoDownloadStateMachine {
    fun reduce(state: VideoDownloadState, event: VideoDownloadEvent): VideoDownloadState =
        when (event) {
            VideoDownloadEvent.Start ->
                VideoDownloadState.Downloading(0f)

            is VideoDownloadEvent.Progress -> when (state) {
                is VideoDownloadState.Downloading ->
                    VideoDownloadState.Downloading(event.fraction.coerceIn(0f, 1f))
                else -> state
            }

            VideoDownloadEvent.Cancel -> when (state) {
                is VideoDownloadState.Downloading -> VideoDownloadState.NotDownloaded
                else -> state
            }

            VideoDownloadEvent.Complete -> when (state) {
                is VideoDownloadState.Downloading -> VideoDownloadState.Downloaded
                else -> state
            }

            is VideoDownloadEvent.Fail -> when (state) {
                is VideoDownloadState.Downloading -> VideoDownloadState.Failed(event.reason)
                else -> state
            }

            VideoDownloadEvent.Reset -> VideoDownloadState.NotDownloaded
        }
}