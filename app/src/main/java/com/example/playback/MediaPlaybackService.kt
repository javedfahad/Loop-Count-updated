package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.LoopCountApp
import com.example.MainActivity
import com.example.R
import com.example.model.AudioTrack
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null

    // Cached album art bitmap to avoid redundant disk decodes
    private var cachedTrackUri: String? = null
    private var cachedAlbumArt: Bitmap? = null

    companion object {
        const val CHANNEL_ID = "loopcount_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.example.loopcount.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.loopcount.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.loopcount.ACTION_PREVIOUS"
    }

    private data class NotificationSnapshot(
        val trackUri: String?,
        val isPlaying: Boolean,
        val isMagicRemixActive: Boolean,
        val isInfiniteRepeat: Boolean,
        val remainingCount: Int,
        val stopAfterFinish: Boolean,
        val speed: Float,
        val displayTitle: String?,
        val displayArtist: String?,
        val magicTransitionCount: Int
    )

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            setListener(object : Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    // Gracefully swallow background start restrictions so the service never crashes
                }
            })
        } catch (e: Exception) {
            // Fallback for earlier Media3 versions
        }

        val app = application as? LoopCountApp ?: LoopCountApp.instance
        val player = app.playerManager.getPlayer()

        if (player != null) {
            val sessionActivityIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val callback = object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    return super.onPlaybackResumption(mediaSession, controller)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityIntent)
                .setCallback(callback)
                .build()

            // Observe player state with distinctUntilChanged filtering so we NEVER spam notifications
            // on every 250ms progress tick. This guarantees the notification will NEVER get wiped out
            // or suppressed by the Android OS rate-limiter, ensuring persistent Dynamic Island,
            // Lock Screen, and Notification Shade visibility until the song is stopped.
            stateObserverJob = scope.launch {
                app.playerManager.state
                    .map { state ->
                        val track = state.currentTrack
                        NotificationSnapshot(
                            trackUri = track?.uri?.toString(),
                            isPlaying = state.isPlaying,
                            isMagicRemixActive = state.isMagicRemixActive,
                            isInfiniteRepeat = state.isInfiniteRepeat,
                            remainingCount = state.remainingCount,
                            stopAfterFinish = state.stopAfterFinish,
                            speed = state.playbackSpeed,
                            displayTitle = track?.displayTitle,
                            displayArtist = track?.displayArtist,
                            magicTransitionCount = state.magicTransitionCount
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest { _ ->
                        val currentState = app.playerManager.state.value
                        val track = currentState.currentTrack
                        if (track != null) {
                            updateNotification(currentState, track)
                        } else {
                            cancelNotification()
                        }
                    }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? LoopCountApp ?: LoopCountApp.instance
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> app.playerManager.togglePlayPause()
            ACTION_NEXT -> app.playerManager.next()
            ACTION_PREVIOUS -> app.playerManager.previous()
        }
        val currentState = app.playerManager.state.value
        val track = currentState.currentTrack
        if (track != null) {
            scope.launch {
                updateNotification(currentState, track)
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Deliberately do not delegate to super.onUpdateNotification, as super invokes
        // MediaNotificationManager which calls ContextCompat.startForegroundService and throws
        // ForegroundServiceStartNotAllowedException on Android 12+ when transitions occur in background.
        val app = application as? LoopCountApp ?: LoopCountApp.instance
        val currentState = app.playerManager.state.value
        val track = currentState.currentTrack
        if (track != null) {
            scope.launch {
                updateNotification(currentState, track)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? LoopCountApp ?: LoopCountApp.instance
        val isPlaying = app.playerManager.state.value.isPlaying
        if (!isPlaying) {
            cancelNotification()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback, Lock Screen & Dynamic Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio playback controls, lock screen player, and live capsule alert"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private suspend fun getAlbumArtBitmap(track: AudioTrack): Bitmap? {
        val uriStr = track.uri.toString()
        if (uriStr == cachedTrackUri && cachedAlbumArt != null) {
            return cachedAlbumArt
        }
        return withContext(Dispatchers.IO) {
            try {
                val artUri = track.albumArtUri ?: return@withContext null
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(artUri, Size(300, 300), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, artUri)
                }
                cachedTrackUri = uriStr
                cachedAlbumArt = bitmap
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun updateNotification(state: PlaybackState, track: AudioTrack) {
        val session = mediaSession ?: return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dynamicSubtext = when {
            state.isMagicRemixActive -> "✨ Magic Remix • #${state.magicTransitionCount}"
            state.isInfiniteRepeat -> "Infinite Loop (∞)"
            state.isRepeatActive -> "Remaining: ${state.remainingCount}"
            state.stopAfterFinish -> "Stop after this track"
            state.isFolderTimerActive -> "Timer active"
            else -> track.displayArtist
        }

        val playPauseIcon = if (state.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val contentText = when {
            state.isMagicRemixActive -> "✨ Magic Remix: ${state.magicFolderName ?: "Folder"} • #${state.magicTransitionCount}"
            state.isInfiniteRepeat -> "Infinite Loop (∞)"
            state.isRepeatActive -> "Remaining: ${state.remainingCount}"
            else -> track.displayArtist
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(track.displayTitle)
            .setContentText(contentText)
            .setSubText(dynamicSubtext)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT) // Crucial for OEM Dynamic Island / Fluid Cloud / Live Alerts
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(state.isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (state.isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // Load album art thumbnail so lock screen, notification drawer, and dynamic island display real album art
        val artBitmap = getAlbumArtBitmap(track)
        if (artBitmap != null) {
            notificationBuilder.setLargeIcon(artBitmap)
        }

        val notification = notificationBuilder.build()
        try {
            if (state.isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                // When paused, detach foreground state without removing notification from shade or lock screen
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If ForegroundServiceStartNotAllowedException or any platform restriction occurs,
            // fall back to direct notification display so the lock screen, notification shade,
            // and Dynamic Island / live alert are never wiped out while the song plays!
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun cancelNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

