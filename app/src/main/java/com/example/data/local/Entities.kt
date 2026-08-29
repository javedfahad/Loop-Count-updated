package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "folder_tracks",
    primaryKeys = ["folderId", "trackUri"]
)
data class FolderTrackEntity(
    val folderId: Long,
    val trackUri: String,
    val trackId: Long,
    val orderIndex: Int,
    val trackTitle: String,
    val trackArtist: String,
    val trackDurationMs: Long,
    val trackAlbum: String = "",
    val albumId: Long = 0L
)

@Entity(tableName = "track_positions")
data class TrackPositionEntity(
    @PrimaryKey val trackUri: String,
    val positionMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_custom_names")
data class TrackCustomNameEntity(
    @PrimaryKey val trackUri: String,
    val customTitle: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "deleted_tracks")
data class DeletedTrackEntity(
    @PrimaryKey val trackUri: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "SYSTEM",
    val accentColor: String = "PURPLE"
)

@Entity(tableName = "folder_positions")
data class FolderPositionEntity(
    @PrimaryKey val folderKey: String,
    val trackUri: String,
    val trackTitle: String = "",
    val positionMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
