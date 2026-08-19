package com.example.model

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderName: String,
    val albumId: Long = 0L,
    val dateAdded: Long = 0L,
    val relativePath: String = ""
) {
    val displayArtist: String
        get() = if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else artist

    val displayTitle: String
        get() = if (title.isBlank()) "Untitled Audio" else title

    val formattedDuration: String
        get() = formatDuration(durationMs)

    companion object {
        fun formatDuration(ms: Long): String {
            if (ms <= 0) return "00:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                val remainingMinutes = minutes % 60
                String.format("%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
            } else {
                String.format("%02d:%02d", minutes, remainingSeconds)
            }
        }
    }
}

data class DeviceFolder(
    val name: String,
    val path: String,
    val trackCount: Int,
    val tracks: List<AudioTrack> = emptyList()
)

data class UserFolder(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val tracks: List<AudioTrack> = emptyList()
)
