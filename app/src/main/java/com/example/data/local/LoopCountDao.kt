package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LoopCountDao {
    // --- User Folders ---
    @Query("SELECT * FROM user_folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM user_folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM user_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    // --- Folder Tracks (Ordered) ---
    @Query("SELECT * FROM folder_tracks WHERE folderId = :folderId ORDER BY orderIndex ASC")
    fun getTracksForFolder(folderId: Long): Flow<List<FolderTrackEntity>>

    @Query("SELECT * FROM folder_tracks WHERE folderId = :folderId ORDER BY orderIndex ASC")
    suspend fun getTracksForFolderSync(folderId: Long): List<FolderTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderTracks(tracks: List<FolderTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderTrack(track: FolderTrackEntity)

    @Query("DELETE FROM folder_tracks WHERE folderId = :folderId AND trackUri = :trackUri")
    suspend fun deleteTrackFromFolder(folderId: Long, trackUri: String)

    @Query("DELETE FROM folder_tracks WHERE folderId = :folderId")
    suspend fun clearFolderTracks(folderId: Long)

    @Query("DELETE FROM folder_tracks WHERE trackUri = :trackUri")
    suspend fun removeTrackFromAllFolders(trackUri: String)

    @Transaction
    suspend fun updateFolderTrackOrder(folderId: Long, orderedTracks: List<FolderTrackEntity>) {
        clearFolderTracks(folderId)
        val indexed = orderedTracks.mapIndexed { index, track ->
            track.copy(orderIndex = index)
        }
        insertFolderTracks(indexed)
    }

    // --- Track Positions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackPosition(position: TrackPositionEntity)

    @Query("SELECT positionMs FROM track_positions WHERE trackUri = :trackUri")
    suspend fun getTrackPosition(trackUri: String): Long?

    @Query("DELETE FROM track_positions WHERE trackUri = :trackUri")
    suspend fun deleteTrackPosition(trackUri: String)

    // --- App Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}
