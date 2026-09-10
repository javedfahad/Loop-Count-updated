package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.LoopCountDatabase
import com.example.data.repository.AudioRepository
import com.example.playback.AudioPlayerManager
import com.example.transfer.WifiTransferManager
import java.io.File

class LoopCountApp : Application(), ImageLoaderFactory {
    val database: LoopCountDatabase by lazy {
        LoopCountDatabase.getDatabase(this)
    }

    val repository: AudioRepository by lazy {
        AudioRepository(this, database.dao())
    }

    val playerManager: AudioPlayerManager by lazy {
        AudioPlayerManager(this, repository)
    }

    val transferManager: WifiTransferManager by lazy {
        WifiTransferManager(this)
    }

    val bluetoothSyncManager: com.example.sync.BluetoothSyncManager by lazy {
        com.example.sync.BluetoothSyncManager(this, playerManager)
    }

    companion object {
        lateinit var instance: LoopCountApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Connect player events to Bluetooth Dual Sync
        playerManager.onSyncEvent = { event, pos, track ->
            if (bluetoothSyncManager.isSyncConnected()) {
                when (event) {
                    "PLAY" -> bluetoothSyncManager.onUserPlay(pos)
                    "PAUSE" -> bluetoothSyncManager.onUserPause(pos)
                    "SEEK" -> bluetoothSyncManager.onUserSeek(pos)
                    "TRACK" -> track?.let { bluetoothSyncManager.sendTrackChange(it, pos, true) }
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Limit memory cache to 15% of available app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(20L * 1024 * 1024) // 20 MB max disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    /**
     * Cleans up temporary caches and trims memory.
     */
    fun clearAppCache() {
        try {
            val imageCacheDir = File(cacheDir, "image_cache")
            if (imageCacheDir.exists()) {
                imageCacheDir.deleteRecursively()
            }
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

