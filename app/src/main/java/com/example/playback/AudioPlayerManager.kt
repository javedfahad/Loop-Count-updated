package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.repository.AudioRepository
import com.example.model.AudioTrack
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AudioPlayerManager(
    private val context: Context,
    private val repository: AudioRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var exoPlayer: ExoPlayer? = null
    private var positionUpdateJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionTracker()
            } else {
                saveCurrentTrackPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: it.durationMs
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    handleTrackEndedNaturally()
                }
                Player.STATE_IDLE -> {
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // When media item transitions naturally or manually
            if (mediaItem != null) {
                val currentTrack = _state.value.currentTrack
                _state.update {
                    it.copy(
                        durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: currentTrack?.durationMs ?: 0L,
                        currentPositionMs = 0L
                    )
                }
            }
        }
    }

    init {
        initPlayer()
    }

    private var forwardingPlayer: ForwardingPlayer? = null

    private fun initPlayer() {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val basePlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build().apply {
                    addListener(playerListener)
                }
            exoPlayer = basePlayer

            forwardingPlayer = object : ForwardingPlayer(basePlayer) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }

                override fun isCommandAvailable(command: @Player.Command Int): Boolean {
                    return when (command) {
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                        else -> super.isCommandAvailable(command)
                    }
                }

                override fun hasNextMediaItem(): Boolean = true
                override fun hasPreviousMediaItem(): Boolean = true

                override fun seekToNext() {
                    this@AudioPlayerManager.next()
                }

                override fun seekToNextMediaItem() {
                    this@AudioPlayerManager.next()
                }

                override fun seekToPrevious() {
                    this@AudioPlayerManager.previous()
                }

                override fun seekToPreviousMediaItem() {
                    this@AudioPlayerManager.previous()
                }
            }
        }
    }

    fun getPlayer(): Player? = forwardingPlayer ?: exoPlayer

    fun updateMediaMetadata() {
        val player = exoPlayer ?: return
        val currentTrack = _state.value.currentTrack ?: return
        val mediaItem = buildMediaItem(currentTrack)
        try {
            if (player.mediaItemCount > 0) {
                player.replaceMediaItem(0, mediaItem)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun startPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        _state.update {
                            it.copy(
                                currentPositionMs = pos,
                                durationMs = if (dur > 0) dur else it.durationMs
                            )
                        }

                        // Check folder timer
                        val currentState = _state.value
                        if (currentState.isFolderTimerActive && !currentState.folderTimerExpired) {
                            val elapsed = System.currentTimeMillis() - currentState.folderTimerStartTime
                            if (elapsed >= currentState.folderTimerDurationMs) {
                                _state.update { it.copy(folderTimerExpired = true) }
                            }
                        }
                    }
                }
                delay(400)
            }
        }
    }

    private fun saveCurrentTrackPosition() {
        val currentTrack = _state.value.currentTrack ?: return
        val pos = exoPlayer?.currentPosition ?: return
        if (pos > 1000L) {
            scope.launch(Dispatchers.IO) {
                repository.saveTrackPosition(currentTrack.uri.toString(), pos)
            }
        }
    }

    // --- Core Playback Methods ---

    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = listOf(track), startPositionMs: Long = 0L) {
        val player = exoPlayer ?: return
        val queueIndex = queue.indexOfFirst { it.uri == track.uri }.let { if (it >= 0) it else 0 }

        _state.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = queueIndex,
                currentPositionMs = startPositionMs,
                durationMs = track.durationMs
            )
        }

        try {
            val serviceIntent = android.content.Intent(context, MediaPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Ignore if service start is restricted by background execution limits
        }

        val mediaItem = buildMediaItem(track)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    fun resumeTrack(track: AudioTrack, queue: List<AudioTrack> = listOf(track)) {
        scope.launch {
            val savedPos = repository.getSavedPosition(track.uri.toString())
            playTrack(track, queue, startPositionMs = savedPos)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    fun stop() {
        exoPlayer?.stop()
        _state.update {
            it.copy(
                isPlaying = false,
                isFolderTimerActive = false,
                folderTimerExpired = false,
                currentPositionMs = 0L
            )
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward10() {
        val player = exoPlayer ?: return
        val current = player.currentPosition
        val target = (current + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        _state.update { it.copy(currentPositionMs = target) }
    }

    fun seekBackward10() {
        val player = exoPlayer ?: return
        val current = player.currentPosition
        val target = (current - 10_000L).coerceAtLeast(0L)
        player.seekTo(target)
        _state.update { it.copy(currentPositionMs = target) }
    }

    fun next() {
        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isNotEmpty()) {
            var nextIndex = currentState.queueIndex + 1
            if (nextIndex >= queue.size) {
                nextIndex = 0 // loop queue
            }
            val nextTrack = queue.getOrNull(nextIndex)
            if (nextTrack != null && (queue.size > 1 || nextTrack.uri != currentState.currentTrack?.uri)) {
                playTrack(nextTrack, queue, 0L)
                return
            }
        }

        // If queue only has 1 track or is empty, load all available device audio tracks
        scope.launch {
            val allTracks = repository.loadDeviceAudioTracks()
            if (allTracks.isNotEmpty()) {
                val curIndex = allTracks.indexOfFirst { it.uri == currentState.currentTrack?.uri }
                val nextIndex = if (curIndex >= 0 && curIndex + 1 < allTracks.size) curIndex + 1 else 0
                val nextTrack = allTracks[nextIndex]
                playTrack(nextTrack, allTracks, 0L)
            } else if (queue.isNotEmpty()) {
                // Repeat the single track
                val singleTrack = queue.first()
                playTrack(singleTrack, queue, 0L)
            }
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        // If played more than 3 seconds, previous restarts the current song
        if (currentPos > 3000L) {
            player.seekTo(0)
            _state.update { it.copy(currentPositionMs = 0L) }
            return
        }

        val currentState = _state.value
        val queue = currentState.queue
        if (queue.isNotEmpty()) {
            var prevIndex = currentState.queueIndex - 1
            if (prevIndex < 0) {
                prevIndex = queue.size - 1
            }
            val prevTrack = queue.getOrNull(prevIndex)
            if (prevTrack != null && (queue.size > 1 || prevTrack.uri != currentState.currentTrack?.uri)) {
                playTrack(prevTrack, queue, 0L)
                return
            }
        }

        // If queue only has 1 track or is empty, load device tracks
        scope.launch {
            val allTracks = repository.loadDeviceAudioTracks()
            if (allTracks.isNotEmpty()) {
                val curIndex = allTracks.indexOfFirst { it.uri == currentState.currentTrack?.uri }
                val prevIndex = if (curIndex > 0) curIndex - 1 else allTracks.size - 1
                val prevTrack = allTracks[prevIndex]
                playTrack(prevTrack, allTracks, 0L)
            } else if (queue.isNotEmpty()) {
                playTrack(queue.first(), queue, 0L)
            }
        }
    }

    fun toggleShuffle() {
        _state.update {
            val newShuffle = !it.isShuffle
            val currentTrack = it.currentTrack
            val newQueue = if (newShuffle) {
                val shuffled = it.queue.shuffled().toMutableList()
                if (currentTrack != null) {
                    shuffled.remove(currentTrack)
                    shuffled.add(0, currentTrack)
                }
                shuffled
            } else {
                it.queue
            }
            it.copy(
                isShuffle = newShuffle,
                queue = newQueue,
                queueIndex = currentTrack?.let { t -> newQueue.indexOfFirst { q -> q.uri == t.uri } } ?: 0
            )
        }
    }

    // --- REPEAT COUNT & STOP LOGIC (CRITICAL) ---

    /**
     * Set Repeat Count and Stop After Finish
     * count = 0 means OFF
     * count > 0 (1..15, 108, etc.) means exactly count full repetitions
     * Start at: remainingCount = count (e.g. 11)
     */
    fun setRepeatCount(count: Int, stopAfterFinish: Boolean) {
        val safeCount = count.coerceAtLeast(0)
        _state.update {
            it.copy(
                repeatCountTotal = safeCount,
                remainingCount = safeCount,
                stopAfterFinish = stopAfterFinish
            )
        }
        updateMediaMetadata()
        // During playback, applying any custom repeat or stop setting restarts the song from the beginning
        exoPlayer?.let { player ->
            if (player.mediaItemCount > 0) {
                player.seekTo(0)
                if (!player.isPlaying) {
                    player.play()
                }
            }
        }
        _state.update { it.copy(currentPositionMs = 0L) }
    }

    fun setStopAfterCurrentTrack(enabled: Boolean) {
        _state.update { it.copy(stopAfterFinish = enabled) }
        updateMediaMetadata()
        if (enabled) {
            // During playback, applying any custom repeat or stop setting restarts the song from the beginning
            exoPlayer?.let { player ->
                if (player.mediaItemCount > 0) {
                    player.seekTo(0)
                    if (!player.isPlaying) {
                        player.play()
                    }
                }
            }
            _state.update { it.copy(currentPositionMs = 0L) }
        }
    }

    /**
     * Invoked strictly when the audio reaches STATE_ENDED naturally.
     */
    private fun handleTrackEndedNaturally() {
        val currentState = _state.value
        val player = exoPlayer ?: return

        // 1. Check folder timer expiration
        if (currentState.isFolderTimerActive) {
            val elapsed = System.currentTimeMillis() - currentState.folderTimerStartTime
            if (elapsed >= currentState.folderTimerDurationMs || currentState.folderTimerExpired) {
                // Folder timer reached -> Current audio finished naturally -> STOP!
                player.stop()
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isFolderTimerActive = false,
                        folderTimerExpired = false
                    )
                }
                updateMediaMetadata()
                return
            }
        }

        // 2. Check Repeat Count
        if (currentState.repeatCountTotal > 0) {
            val currentRemaining = currentState.remainingCount
            val nextRemaining = currentRemaining - 1

            if (nextRemaining > 0) {
                // More repetitions remain -> update remaining and replay current track from 0
                _state.update { it.copy(remainingCount = nextRemaining) }
                updateMediaMetadata()
                player.seekTo(0)
                player.play()
                return
            } else {
                // All repetitions completed! Reset remaining count to 0 and repeatCountTotal to 0
                _state.update {
                    it.copy(
                        repeatCountTotal = 0,
                        remainingCount = 0
                    )
                }
                updateMediaMetadata()

                // Check if user ALSO selected "Stop After Finish"
                if (currentState.stopAfterFinish) {
                    // STOP after repeat sequence completes!
                    player.pause()
                    player.seekTo(0)
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            stopAfterFinish = false
                        )
                    }
                    updateMediaMetadata()
                    return
                } else {
                    // Repeat sequence finished, but Stop is NOT enabled -> proceed to next song in queue/library!
                    val queue = currentState.queue
                    if (queue.isNotEmpty()) {
                        val nextIndex = currentState.queueIndex + 1
                        if (nextIndex < queue.size) {
                            val nextTrack = queue[nextIndex]
                            playTrack(nextTrack, queue, 0L)
                            return
                        }
                    }
                    // If queue ended, loop queue or load next device track
                    next()
                    return
                }
            }
        }

        // 3. Repeat is OFF: Check Stop After Finish
        if (currentState.stopAfterFinish) {
            // Stop after this single playback naturally finishes!
            player.pause()
            player.seekTo(0)
            _state.update {
                it.copy(
                    isPlaying = false,
                    stopAfterFinish = false
                )
            }
            updateMediaMetadata()
            return
        }

        // 4. Normal sequential playback (Repeat OFF, Stop OFF)
        val queue = currentState.queue
        if (queue.isNotEmpty()) {
            val nextIndex = currentState.queueIndex + 1
            if (nextIndex < queue.size) {
                val nextTrack = queue[nextIndex]
                playTrack(nextTrack, queue, 0L)
            } else {
                // End of queue
                player.pause()
                player.seekTo(0)
                _state.update { it.copy(isPlaying = false) }
                updateMediaMetadata()
            }
        }
    }

    // --- Folder "Play for..." Timer ---

    fun startFolderTimer(durationMinutes: Int) {
        val durationMs = durationMinutes * 60 * 1000L
        _state.update {
            it.copy(
                folderTimerDurationMs = durationMs,
                folderTimerStartTime = System.currentTimeMillis(),
                isFolderTimerActive = true,
                folderTimerExpired = false
            )
        }
        updateMediaMetadata()
    }

    fun cancelFolderTimer() {
        _state.update {
            it.copy(
                isFolderTimerActive = false,
                folderTimerExpired = false
            )
        }
        updateMediaMetadata()
    }

    private fun buildMediaItem(track: AudioTrack): MediaItem {
        val currentState = _state.value
        val loopSubtitle = when {
            currentState.isRepeatActive -> "🔁 ${currentState.remainingCount} loops remaining"
            currentState.stopAfterFinish -> "⏹ Stop after this track"
            currentState.isFolderTimerActive -> "⏳ Timer active"
            else -> track.displayArtist
        }

        val displayTitle = if (currentState.isRepeatActive) {
            "${track.displayTitle} [↺ ${currentState.remainingCount} left]"
        } else {
            track.displayTitle
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(track.displayArtist)
            .setSubtitle(loopSubtitle)
            .setDescription(loopSubtitle)
            .setAlbumTitle(track.album.ifBlank { "LoopCount" })
            .setDisplayTitle(displayTitle)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.uri.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        positionUpdateJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }
}
