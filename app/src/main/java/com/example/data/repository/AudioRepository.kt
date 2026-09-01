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
import com.example.data.local.DeletedTrackEntity
import com.example.data.local.FolderEntity
import com.example.data.local.FolderPositionEntity
import com.example.data.local.FolderTrackEntity
import com.example.data.local.LoopCountDao
import com.example.data.local.PersistentStorageManager
import com.example.data.local.TrackCustomNameEntity
import com.example.data.local.TrackPositionEntity
import com.example.model.AudioTrack
import com.example.model.DeviceFolder
import com.example.model.UserFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepository(
    private val context: Context,
    private val dao: LoopCountDao
) {
    private val persistentStorage = PersistentStorageManager(context)

    suspend fun restoreAndSyncPersistentStorage() = withContext(Dispatchers.IO) {
        try {
            // 1. Sync User Folders
            val dbFolders = dao.getAllFoldersSync()
            if (dbFolders.isEmpty()) {
                val backupFolders = persistentStorage.loadFolders()
                if (backupFolders.isNotEmpty()) {
                    for (folder in backupFolders) {
                        val folderId = dao.insertFolder(FolderEntity(id = if (folder.id > 0) folder.id else 0L, name = folder.name, createdAt = folder.createdAt))
                        val entities = folder.tracks.mapIndexed { idx, tr ->
                            FolderTrackEntity(
                                folderId = folderId,
                                trackUri = tr.uri.toString(),
                                trackId = tr.id,
                                orderIndex = idx,
                                trackTitle = tr.title,
                                trackArtist = tr.artist,
                                trackDurationMs = tr.durationMs,
                                trackAlbum = tr.album,
                                albumId = tr.albumId,
                                dateAdded = tr.dateAdded
                            )
                        }
                        dao.insertFolderTracks(entities)
                    }
                }
            } else {
                syncFoldersToPersistentFile()
            }

            // 2. Sync Folder Resume Positions
            val dbFolderPositions = dao.getAllFolderPositionsSync()
            if (dbFolderPositions.isEmpty()) {
                val backupFolderPositions = persistentStorage.loadFolderPositions()
                for (pos in backupFolderPositions) {
                    dao.saveFolderPosition(pos)
                }
            } else {
                val map = dbFolderPositions.associateBy { it.folderKey }
                persistentStorage.saveFolderPositions(map)
            }

            // 3. Sync Track Positions
            val dbTrackPositions = dao.getAllTrackPositionsSync()
            if (dbTrackPositions.isEmpty()) {
                val backupTrackPositions = persistentStorage.loadTrackPositions()
                for ((uri, pos) in backupTrackPositions) {
                    dao.saveTrackPosition(TrackPositionEntity(trackUri = uri, positionMs = pos))
                }
            } else {
                val map = dbTrackPositions.associate { it.trackUri to it.positionMs }
                persistentStorage.saveTrackPositions(map)
            }

            // 4. Sync Custom Track Names
            val dbCustomNames = dao.getAllCustomTrackNamesSync()
            if (dbCustomNames.isEmpty()) {
                val backupNames = persistentStorage.loadCustomNames()
                for ((uri, name) in backupNames) {
                    dao.saveCustomTrackName(TrackCustomNameEntity(trackUri = uri, customTitle = name))
                }
            } else {
                val map = dbCustomNames.associate { it.trackUri to it.customTitle }
                persistentStorage.saveCustomNames(map)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncFoldersToPersistentFile() {
        try {
            val entities = dao.getAllFoldersSync()
            val userFolders = entities.map { entity ->
                val tracks = dao.getTracksForFolderSync(entity.id)
                UserFolder(
                    id = entity.id,
                    name = entity.name,
                    createdAt = entity.createdAt,
                    tracks = tracks.map { ft ->
                        AudioTrack(
                            id = ft.trackId,
                            uri = Uri.parse(ft.trackUri),
                            title = ft.trackTitle,
                            artist = ft.trackArtist,
                            album = ft.trackAlbum,
                            durationMs = ft.trackDurationMs,
                            folderName = entity.name,
                            albumId = ft.albumId,
                            dateAdded = ft.dateAdded
                        )
                    }
                )
            }
            persistentStorage.saveFolders(userFolders)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncFolderPositionsToPersistentFile() {
        try {
            val positions = dao.getAllFolderPositionsSync().associateBy { it.folderKey }
            persistentStorage.saveFolderPositions(positions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncTrackPositionsToPersistentFile() {
        try {
            val positions = dao.getAllTrackPositionsSync().associate { it.trackUri to it.positionMs }
            persistentStorage.saveTrackPositions(positions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncCustomNamesToPersistentFile() {
        try {
            val names = dao.getAllCustomTrackNamesSync().associate { it.trackUri to it.customTitle }
            persistentStorage.saveCustomNames(names)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // --- Load Audio Tracks from MediaStore ---
    suspend fun loadDeviceAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val trackList = mutableListOf<AudioTrack>()
        val deletedUris = try {
            dao.getDeletedTrackUrisSync().toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val customNames = try {
            dao.getAllCustomTrackNamesSync().associate { it.trackUri to it.customTitle }
        } catch (e: Exception) {
            emptyMap()
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
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
                val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
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
                    val rawDateAdded = if (dateAddedCol != -1) it.getLong(dateAddedCol) else 0L
                    val rawDateModified = if (dateModifiedCol != -1) it.getLong(dateModifiedCol) else 0L
                    val dateAdded = when {
                        rawDateAdded > 0 && rawDateModified > 0 -> maxOf(rawDateAdded, rawDateModified)
                        rawDateAdded > 0 -> rawDateAdded
                        rawDateModified > 0 -> rawDateModified
                        else -> id.coerceAtLeast(0L)
                    }
                    val rawPath = if (pathCol != -1) it.getString(pathCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (deletedUris.contains(contentUri.toString())) {
                        continue
                    }

                    val folderName = extractFolderName(rawPath)

                    val defaultTitle = if (rawTitle.isBlank()) {
                        "Audio $id"
                    } else {
                        rawTitle
                    }
                    val title = customNames[contentUri.toString()] ?: defaultTitle

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

        // Include built-in demo tracks if not explicitly deleted
        try {
            val demoTracks = DemoAudioGenerator.getOrGenerateDemoTracks(context)
            val nonDeletedDemos = demoTracks.filter { !deletedUris.contains(it.uri.toString()) }
                .map { demo ->
                    val customTitle = customNames[demo.uri.toString()]
                    if (customTitle != null) demo.copy(title = customTitle) else demo
                }
            trackList.addAll(0, nonDeletedDemos)
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
        return combine(
            dao.getAllFolders(),
            dao.getAllFolderTracks()
        ) { folderEntities, trackEntities ->
            val tracksByFolder = trackEntities.groupBy { it.folderId }
            folderEntities.map { entity ->
                val folderTracks = tracksByFolder[entity.id] ?: emptyList()
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
                            albumId = ft.albumId,
                            dateAdded = ft.dateAdded
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
                    albumId = ft.albumId,
                    dateAdded = ft.dateAdded
                )
            }
        }
    }

    suspend fun createUserFolder(name: String): Long = withContext(Dispatchers.IO) {
        val id = dao.insertFolder(FolderEntity(name = name.trim()))
        syncFoldersToPersistentFile()
        id
    }

    suspend fun renameUserFolder(folderId: Long, newName: String) = withContext(Dispatchers.IO) {
        val folder = dao.getFolderById(folderId)
        if (folder != null) {
            dao.updateFolder(folder.copy(name = newName.trim()))
            syncFoldersToPersistentFile()
        }
    }

    suspend fun deleteUserFolder(folderId: Long) = withContext(Dispatchers.IO) {
        dao.clearFolderTracks(folderId)
        dao.deleteFolder(folderId)
        syncFoldersToPersistentFile()
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
                albumId = track.albumId,
                dateAdded = track.dateAdded
            )
        }
        dao.insertFolderTracks(entities)
        syncFoldersToPersistentFile()
    }

    suspend fun removeTrackFromUserFolder(folderId: Long, trackUri: String) = withContext(Dispatchers.IO) {
        dao.deleteTrackFromFolder(folderId, trackUri)
        // Re-index remaining tracks
        val remaining = dao.getTracksForFolderSync(folderId)
        dao.updateFolderTrackOrder(folderId, remaining)
        syncFoldersToPersistentFile()
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
                albumId = track.albumId,
                dateAdded = track.dateAdded
            )
        }
        dao.updateFolderTrackOrder(folderId, entities)
        syncFoldersToPersistentFile()
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
        syncTrackPositionsToPersistentFile()
    }

    suspend fun getSavedPosition(trackUri: String): Long = withContext(Dispatchers.IO) {
        val dbPos = dao.getTrackPosition(trackUri)
        if (dbPos != null && dbPos > 0) {
            dbPos
        } else {
            val map = persistentStorage.loadTrackPositions()
            map[trackUri] ?: 0L
        }
    }

    // --- Folder Playback Positions (Resume Folder Feature) ---
    suspend fun saveFolderPosition(folderKey: String, trackUri: String, trackTitle: String, positionMs: Long) = withContext(Dispatchers.IO) {
        if (folderKey.isNotBlank() && trackUri.isNotBlank()) {
            dao.saveFolderPosition(
                FolderPositionEntity(
                    folderKey = folderKey,
                    trackUri = trackUri,
                    trackTitle = trackTitle,
                    positionMs = positionMs,
                    updatedAt = System.currentTimeMillis()
                )
            )
            syncFolderPositionsToPersistentFile()
        }
    }

    suspend fun getFolderPosition(folderKey: String): FolderPositionEntity? = withContext(Dispatchers.IO) {
        if (folderKey.isBlank()) return@withContext null
        val dbPos = dao.getFolderPosition(folderKey)
        if (dbPos != null) {
            dbPos
        } else {
            val list = persistentStorage.loadFolderPositions()
            list.find { it.folderKey == folderKey }
        }
    }

    suspend fun deleteFolderPosition(folderKey: String) = withContext(Dispatchers.IO) {
        dao.deleteFolderPosition(folderKey)
        syncFolderPositionsToPersistentFile()
    }

    // --- Rename Track ---
    suspend fun renameTrack(track: AudioTrack, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) return@withContext false

        try {
            // 1. Save custom title in local Room Database so it persists forever
            dao.saveCustomTrackName(
                TrackCustomNameEntity(
                    trackUri = track.uri.toString(),
                    customTitle = trimmedTitle
                )
            )
            syncCustomNamesToPersistentFile()

            // 2. Update folder track items in user folders
            dao.updateTrackTitleInFolders(track.uri.toString(), trimmedTitle)
            syncFoldersToPersistentFile()

            // 3. Attempt to update MediaStore if applicable (no crash if scoped storage restricts)
            if (track.uri.scheme == "content") {
                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, trimmedTitle)
                    }
                    context.contentResolver.update(track.uri, contentValues, null, null)
                } catch (e: Exception) {
                    // Ignored on Android 10+ scoped storage - Room custom name handles it perfectly
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Delete Track ---
    suspend fun cleanupDeletedTrack(trackUri: String) = withContext(Dispatchers.IO) {
        try {
            dao.markTrackDeleted(DeletedTrackEntity(trackUri = trackUri))
            dao.removeTrackFromAllFolders(trackUri)
            dao.deleteTrackPosition(trackUri)
            dao.deleteCustomTrackName(trackUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeMultipleTracksFromUserFolder(folderId: Long, trackUris: List<String>) = withContext(Dispatchers.IO) {
        for (uri in trackUris) {
            dao.deleteTrackFromFolder(folderId, uri)
        }
        val remaining = dao.getTracksForFolderSync(folderId)
        dao.updateFolderTrackOrder(folderId, remaining)
        syncFoldersToPersistentFile()
    }

    suspend fun deleteMultipleTracks(tracks: List<AudioTrack>): DeleteResult = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext DeleteResult.Success

        val fileTracks = tracks.filter { it.uri.scheme == "file" }
        val contentTracks = tracks.filter { it.uri.scheme == "content" }

        // 1. Delete all file tracks directly
        for (ft in fileTracks) {
            val file = ft.uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                file.delete()
            }
            cleanupDeletedTrack(ft.uri.toString())
        }

        if (contentTracks.isEmpty()) {
            return@withContext DeleteResult.Success
        }

        // 2. Delete content tracks via MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intentSender = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    contentTracks.map { it.uri }
                ).intentSender
                return@withContext DeleteResult.RequiresUserConsent(intentSender)
            } catch (e: Exception) {
                // Fallback to individual deletes
            }
        }

        for (ct in contentTracks) {
            try {
                context.contentResolver.delete(ct.uri, null, null)
            } catch (e: Exception) {
                // Ignore
            }
            cleanupDeletedTrack(ct.uri.toString())
        }

        DeleteResult.Success
    }

    suspend fun deleteTrack(track: AudioTrack): DeleteResult = withContext(Dispatchers.IO) {
        val trackUriStr = track.uri.toString()
        try {
            // If it's a file:// URI (like demo tracks or local app files)
            if (track.uri.scheme == "file") {
                val file = track.uri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    file.delete()
                }
                cleanupDeletedTrack(trackUriStr)
                return@withContext DeleteResult.Success
            }

            // For MediaStore content:// URIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(track.uri)
                    ).intentSender
                    return@withContext DeleteResult.RequiresUserConsent(intentSender)
                } catch (e: Exception) {
                    // Fallback to direct delete below
                }
            }

            val rows = context.contentResolver.delete(track.uri, null, null)
            if (rows > 0) {
                cleanupDeletedTrack(trackUriStr)
                DeleteResult.Success
            } else {
                cleanupDeletedTrack(trackUriStr)
                DeleteResult.Success
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DeleteResult.RequiresUserConsent(e.userAction.actionIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(track.uri)
                    ).intentSender
                    DeleteResult.RequiresUserConsent(intentSender)
                } catch (ex: Exception) {
                    cleanupDeletedTrack(trackUriStr)
                    DeleteResult.Success
                }
            } else {
                cleanupDeletedTrack(trackUriStr)
                DeleteResult.Success
            }
        } catch (e: Exception) {
            cleanupDeletedTrack(trackUriStr)
            DeleteResult.Success
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
