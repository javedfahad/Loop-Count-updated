package com.example

import android.net.Uri
import com.example.model.AudioTrack
import org.junit.Assert.assertEquals
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
}

