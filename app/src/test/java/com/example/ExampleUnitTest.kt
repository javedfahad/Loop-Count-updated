package com.example

import android.net.Uri
import com.example.model.AudioTrack
import com.example.transfer.NetworkUtils
import com.example.transfer.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
    @Test
    fun testFormatDuration() {
        assertEquals("00:00", AudioTrack.formatDuration(0))
        assertEquals("00:45", AudioTrack.formatDuration(45_000))
        assertEquals("03:25", AudioTrack.formatDuration(205_000))
        assertEquals("1:01:05", AudioTrack.formatDuration(3665_000))
    }

    @Test
    fun testAudioTrackDefaults() {
        val track = AudioTrack(
            id = 1L,
            uri = Uri.parse("content://media/external/audio/media/1"),
            title = "",
            artist = "<unknown>",
            album = "",
            durationMs = 120_000L,
            folderName = "Music"
        )
        assertEquals("Untitled Audio", track.displayTitle)
        assertEquals("Unknown Artist", track.displayArtist)
        assertEquals("02:00", track.formattedDuration)
    }

    @Test
    fun testNetworkUtilsFormatting() {
        assertEquals("500 B", NetworkUtils.formatFileSize(500))
        assertEquals("500 KB", NetworkUtils.formatFileSize(500 * 1024))
        assertEquals("5.0 MB", NetworkUtils.formatFileSize(5 * 1024 * 1024))
        assertEquals("12.5 MB/s", NetworkUtils.formatSpeed(12_500 * 1024))
        assertEquals("my_song", NetworkUtils.sanitizeFileName("my/song*?"))
    }

    @Test
    fun testTransferProgressCalculation() {
        val progress = TransferProgress(
            currentIndex = 2,
            totalCount = 4,
            currentFileBytes = 500L,
            currentFileTotalBytes = 1000L,
            overallBytesTransferred = 2500L,
            overallBytesTotal = 5000L
        )
        assertEquals(0.5f, progress.currentFileProgress, 0.01f)
        assertEquals(0.5f, progress.overallProgress, 0.01f)
    }
}

