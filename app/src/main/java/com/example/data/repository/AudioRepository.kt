package com.example.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.AppSettingsEntity
import com.example.data.local.FolderEntity
import com.example.data.local.FolderTrackEntity
import com.example.data.local.LoopCountDao
import com.example.data.local.TrackPositionEntity
import com.example.model.AudioTrack
import com.example.model.DeviceFolder
import com.example.model.UserFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepository(
    private val context: Context,
    private val dao: LoopCountDao
) {
    // --- Load Audio Tracks from MediaStore ---
    suspend fun loadDeviceAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val trackList = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.RELATIVE_PATH
            } else {
                MediaStore.Audio.Media.DATA
            }
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= 500" // ignore ultra short sounds < 0.5s
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    it.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = it.getString(titleCol) ?: ""
                    val rawArtist = it.getString(artistCol) ?: ""
                    val rawAlbum = it.getString(albumCol) ?: ""
                    val duration = it.getLong(durationCol)
                    val albumId = it.getLong(albumIdCol)
                    val dateAdded = it.getLong(dateAddedCol)
                    val rawPath = if (pathCol != -1) it.getString(pathCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val folderName = extractFolderName(rawPath)

                    val title = if (rawTitle.isBlank()) {
                        "Audio $id"
                    } else {
                        rawTitle
                    }

                    val artist = if (rawArtist.isBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                    trackList.add(
                        AudioTrack(
                            id = id,
                            uri = contentUri,
                            title = title,
                            artist = artist,
                            album = rawAlbum,
                            durationMs = duration,
                            folderName = folderName,
                            albumId = albumId,
                            dateAdded = dateAdded,
                            relativePath = rawPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always include built-in demo tracks so the user can test loop counting & playback immediately
        try {
            val demoTracks = DemoAudioGenerator.getOrGenerateDemoTracks(context)
            trackList.addAll(0, demoTracks)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        trackList
    }

    private fun extractFolderName(path: String): String {
        if (path.isBlank()) return "Internal Storage"
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash >= 0) {
            trimmed.substring(lastSlash + 1)
        } else {
            trimmed.ifBlank { "Music" }
        }
    }

    fun groupIntoDeviceFolders(tracks: List<AudioTrack>): List<DeviceFolder> {
        return tracks.groupBy { it.folderName }
            .map { (folderName, trackList) ->
                DeviceFolder(
                    name = folderName,
                    path = trackList.firstOrNull()?.relativePath ?: folderName,
                    trackCount = trackList.size,
                    tracks = trackList
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // --- User Custom Folders & Persistent Ordering ---
    fun getUserFolders(): Flow<List<UserFolder>> {
        return dao.getAllFolders().map { entities ->
            entities.map { entity ->
                val folderTracks = dao.getTracksForFolderSync(entity.id)
                UserFolder(
                    id = entity.id,
                    name = entity.name,
                    createdAt = entity.createdAt,
                    tracks = folderTracks.map { ft ->
                        AudioTrack(
                            id = ft.trackId,
                            uri = Uri.parse(ft.trackUri),
                            title = ft.trackTitle,
                            artist = ft.trackArtist,
                            album = ft.trackAlbum,
                            durationMs = ft.trackDurationMs,
                            folderName = entity.name,
                            albumId = ft.albumId
                        )
                    }
                )
            }
        }
    }

    fun getFolderTracksFlow(folderId: Long): Flow<List<AudioTrack>> {
        return dao.getTracksForFolder(folderId).map { entities ->
            entities.map { ft ->
                AudioTrack(
                    id = ft.trackId,
                    uri = Uri.parse(ft.trackUri),
                    title = ft.trackTitle,
                    artist = ft.trackArtist,
                    album = ft.trackAlbum,
                    durationMs = ft.trackDurationMs,
                    folderName = "",
                    albumId = ft.albumId
                )
            }
        }
    }

    suspend fun createUserFolder(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertFolder(FolderEntity(name = name.trim()))
    }

    suspend fun renameUserFolder(folderId: Long, newName: String) = withContext(Dispatchers.IO) {
        val folder = dao.getFolderById(folderId)
        if (folder != null) {
            dao.updateFolder(folder.copy(name = newName.trim()))
        }
    }

    suspend fun deleteUserFolder(folderId: Long) = withContext(Dispatchers.IO) {
        dao.clearFolderTracks(folderId)
        dao.deleteFolder(folderId)
    }

    suspend fun addTracksToUserFolder(folderId: Long, tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        val existing = dao.getTracksForFolderSync(folderId)
        var nextIndex = existing.size
        val entities = tracks.map { track ->
            FolderTrackEntity(
                folderId = folderId,
                trackUri = track.uri.toString(),
                trackId = track.id,
                orderIndex = nextIndex++,
                trackTitle = track.title,
                trackArtist = track.artist,
                trackDurationMs = track.durationMs,
                trackAlbum = track.album,
                albumId = track.albumId
            )
        }
        dao.insertFolderTracks(entities)
    }

    suspend fun removeTrackFromUserFolder(folderId: Long, trackUri: String) = withContext(Dispatchers.IO) {
        dao.deleteTrackFromFolder(folderId, trackUri)
        // Re-index remaining tracks
        val remaining = dao.getTracksForFolderSync(folderId)
        dao.updateFolderTrackOrder(folderId, remaining)
    }

    suspend fun updateFolderTrackOrder(folderId: Long, tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        val entities = tracks.mapIndexed { index, track ->
            FolderTrackEntity(
                folderId = folderId,
                trackUri = track.uri.toString(),
                trackId = track.id,
                orderIndex = index,
                trackTitle = track.title,
                trackArtist = track.artist,
                trackDurationMs = track.durationMs,
                trackAlbum = track.album,
                albumId = track.albumId
            )
        }
        dao.updateFolderTrackOrder(folderId, entities)
    }

    // --- Track Playback Positions (Resume Feature) ---
    suspend fun saveTrackPosition(trackUri: String, positionMs: Long) = withContext(Dispatchers.IO) {
        dao.saveTrackPosition(
            TrackPositionEntity(
                trackUri = trackUri,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedPosition(trackUri: String): Long = withContext(Dispatchers.IO) {
        dao.getTrackPosition(trackUri) ?: 0L
    }

    // --- Rename Track ---
    suspend fun renameTrack(track: AudioTrack, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle.trim())
            }
            val rows = context.contentResolver.update(track.uri, contentValues, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Delete Track ---
    suspend fun deleteTrack(track: AudioTrack): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(track.uri, null, null)
            dao.removeTrackFromAllFolders(track.uri.toString())
            dao.deleteTrackPosition(track.uri.toString())
            DeleteResult.Success
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DeleteResult.RequiresUserConsent(e.userAction.actionIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intentSender = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(track.uri)
                ).intentSender
                DeleteResult.RequiresUserConsent(intentSender)
            } else {
                DeleteResult.Error(e.message ?: "Failed to delete")
            }
        } catch (e: Exception) {
            DeleteResult.Error(e.message ?: "Failed to delete")
        }
    }

    // --- Settings ---
    fun getSettings(): Flow<AppSettingsEntity?> = dao.getSettings()

    suspend fun saveSettings(themeMode: String, accentColor: String) = withContext(Dispatchers.IO) {
        dao.saveSettings(
            AppSettingsEntity(
                id = 1,
                themeMode = themeMode,
                accentColor = accentColor
            )
        )
    }
}

sealed class DeleteResult {
    object Success : DeleteResult()
    data class RequiresUserConsent(val intentSender: IntentSender) : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}
