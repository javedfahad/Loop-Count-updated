package com.example.playback

import com.example.model.AudioTrack

data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val queue: List<AudioTrack> = emptyList(),
    val queueIndex: Int = -1,
    val isShuffle: Boolean = false,

    // Repeat Count Feature
    val repeatCountTotal: Int = 0, // 0 = Off, >0 = Total selected (e.g. 1..15, 108)
    val remainingCount: Int = 0,   // Number of complete repetitions remaining
    val stopAfterFinish: Boolean = false, // Stop after current track or repeat sequence finishes

    // Folder Play For Timer
    val folderTimerDurationMs: Long = 0L,
    val folderTimerStartTime: Long = 0L,
    val isFolderTimerActive: Boolean = false,
    val folderTimerExpired: Boolean = false,

    // Magic Remix Mode
    val isMagicRemixActive: Boolean = false,
    val magicFolderName: String? = null,
    val magicSliceDurationMs: Long = 0L,
    val magicSliceStartTimeMs: Long = 0L,
    val magicSliceElapsedMs: Long = 0L,
    val magicTransitionCount: Int = 0
) {
    val isRepeatActive: Boolean
        get() = repeatCountTotal > 0

    val repeatDisplayLabel: String
        get() = when {
            repeatCountTotal <= 0 -> "Off"
            repeatCountTotal == 1 -> "1 Time"
            else -> "$repeatCountTotal Times"
        }

    val magicSliceRemainingSeconds: Int
        get() {
            if (!isMagicRemixActive || magicSliceDurationMs <= 0) return 0
            val rem = (magicSliceDurationMs - magicSliceElapsedMs).coerceAtLeast(0L)
            return (rem / 1000L).toInt()
        }

    val magicSliceProgress: Float
        get() {
            if (!isMagicRemixActive || magicSliceDurationMs <= 0) return 0f
            return (magicSliceElapsedMs.toFloat() / magicSliceDurationMs.toFloat()).coerceIn(0f, 1f)
        }
}
