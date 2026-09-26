package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.LoopCountApp
import com.example.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true

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
        val player = app.playerManager.getPlayer() ?: return

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
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing()) {
            pauseAllPlayersAndStopSelf()
        }
        // If playback is ongoing, allow Media3 to keep the service alive.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
