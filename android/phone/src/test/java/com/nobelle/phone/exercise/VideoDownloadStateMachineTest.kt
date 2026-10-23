package com.nobelle.phone.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadStateMachineTest {
    private val machine = VideoDownloadStateMachine

    @Test
    fun startBeginsAtZeroProgress() {
        val state = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        assertEquals(VideoDownloadState.Downloading(0f), state)
    }

    @Test
    fun progressUpdatesAndClamps() {
        val downloading = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        assertEquals(
            VideoDownloadState.Downloading(0.4f),
            machine.reduce(downloading, VideoDownloadEvent.Progress(0.4f)),
        )
        // Out-of-range fractions are clamped, never crash the pipeline.
        assertEquals(
            VideoDownloadState.Downloading(1f),
            machine.reduce(downloading, VideoDownloadEvent.Progress(7.5f)),
        )
        assertEquals(
            VideoDownloadState.Downloading(0f),
            machine.reduce(downloading, VideoDownloadEvent.Progress(-0.2f)),
        )
    }

    @Test
    fun cancelReturnsToNotDownloaded() {
        val downloading = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        assertEquals(
            VideoDownloadState.NotDownloaded,
            machine.reduce(downloading, VideoDownloadEvent.Cancel),
        )
    }

    @Test
    fun checksumFailureMovesToFailedAndKeepsTheReason() {
        val downloading = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        val failed = machine.reduce(
            downloading, VideoDownloadEvent.Fail("Checksum mismatch — discarded"),
        )
        assertEquals(VideoDownloadState.Failed("Checksum mismatch — discarded"), failed)
        assertTrue((failed as VideoDownloadState.Failed).reason.contains("Checksum"))
    }

    @Test
    fun successMovesToDownloaded() {
        val downloading = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        assertEquals(
            VideoDownloadState.Downloaded,
            machine.reduce(downloading, VideoDownloadEvent.Complete),
        )
    }

    @Test
    fun retryAfterFailureRestartsCleanly() {
        val downloading = machine.reduce(
            VideoDownloadState.NotDownloaded, VideoDownloadEvent.Start,
        )
        val failed = machine.reduce(downloading, VideoDownloadEvent.Fail("Interrupted"))
        assertEquals(
            VideoDownloadState.Downloading(0f),
            machine.reduce(failed, VideoDownloadEvent.Start),
        )
    }

    @Test
    fun eventsOutsideDownloadingAreIgnored() {
        listOf(
            VideoDownloadState.NotAvailable,
            VideoDownloadState.NotDownloaded,
            VideoDownloadState.Downloaded,
            VideoDownloadState.Failed("x"),
        ).forEach { state ->
            assertEquals(
                state,
                machine.reduce(state, VideoDownloadEvent.Progress(0.5f)),
            )
            assertEquals(state, machine.reduce(state, VideoDownloadEvent.Cancel))
            assertEquals(state, machine.reduce(state, VideoDownloadEvent.Complete))
            assertEquals(state, machine.reduce(state, VideoDownloadEvent.Fail("y")))
        }
    }

    @Test
    fun resetClearsADownloadedFile() {
        assertEquals(
            VideoDownloadState.NotDownloaded,
            machine.reduce(VideoDownloadState.Downloaded, VideoDownloadEvent.Reset),
        )
    }
}