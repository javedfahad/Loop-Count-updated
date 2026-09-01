package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        FolderTrackEntity::class,
        TrackPositionEntity::class,
        TrackCustomNameEntity::class,
        DeletedTrackEntity::class,
        AppSettingsEntity::class,
        FolderPositionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LoopCountDatabase : RoomDatabase() {
    abstract fun dao(): LoopCountDao

    companion object {
        @Volatile
        private var INSTANCE: LoopCountDatabase? = null

        fun getDatabase(context: Context): LoopCountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LoopCountDatabase::class.java,
                    "loopcount_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
