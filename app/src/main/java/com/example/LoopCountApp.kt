package com.example

import android.app.Application
import com.example.data.local.LoopCountDatabase
import com.example.data.repository.AudioRepository
import com.example.playback.AudioPlayerManager

class LoopCountApp : Application() {
    val database: LoopCountDatabase by lazy {
        LoopCountDatabase.getDatabase(this)
    }

    val repository: AudioRepository by lazy {
        AudioRepository(this, database.dao())
    }

    val playerManager: AudioPlayerManager by lazy {
        AudioPlayerManager(this, repository)
    }

    companion object {
        lateinit var instance: LoopCountApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
