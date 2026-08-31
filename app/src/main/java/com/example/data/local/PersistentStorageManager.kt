package com.example.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.AudioTrack
import com.example.model.UserFolder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Handles permanent JSON storage in context.filesDir (internal app storage).
 * This storage:
 * 1. Survives app updates and reboots.
 * 2. Survives "Clear Cache".
 * 3. Survives app force stops / restarts.
 * 4. Is ONLY removed when the user clicks "Clear Data" in Android Settings or uninstalls the app.
 */
class PersistentStorageManager(private val context: Context) {

    private val foldersFile = File(context.filesDir, "user_folders_persistent.json")
    private val folderPositionsFile = File(context.filesDir, "folder_positions_persistent.json")
    private val trackPositionsFile = File(context.filesDir, "track_positions_persistent.json")
    private val customNamesFile = File(context.filesDir, "custom_names_persistent.json")

    private val lock = Any()

    // --- USER FOLDERS PERSISTENCE ---

    fun saveFolders(folders: List<UserFolder>) {
        synchronized(lock) {
            try {
                val jsonArray = JSONArray()
                for (folder in folders) {
                    val folderObj = JSONObject()
                    folderObj.put("id", folder.id)
                    folderObj.put("name", folder.name)
                    folderObj.put("createdAt", folder.createdAt)

                    val tracksArray = JSONArray()
                    for (track in folder.tracks) {
                        val trackObj = JSONObject()
                        trackObj.put("id", track.id)
                        trackObj.put("uri", track.uri.toString())
                        trackObj.put("title", track.title)
                        trackObj.put("artist", track.artist)
                        trackObj.put("album", track.album)
                        trackObj.put("durationMs", track.durationMs)
                        trackObj.put("albumId", track.albumId)
                        tracksArray.put(trackObj)
                    }
                    folderObj.put("tracks", tracksArray)
                    jsonArray.put(folderObj)
                }

                writeStringToFileAtomically(foldersFile, jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving folders to persistent storage", e)
            }
        }
    }

    fun loadFolders(): List<UserFolder> {
        synchronized(lock) {
            if (!foldersFile.exists()) return emptyList()
            return try {
                val content = foldersFile.readText()
                if (content.isBlank()) return emptyList()
                val jsonArray = JSONArray(content)
                val list = mutableListOf<UserFolder>()

                for (i in 0 until jsonArray.length()) {
                    val folderObj = jsonArray.getJSONObject(i)
                    val id = folderObj.optLong("id", System.currentTimeMillis() + i)
                    val name = folderObj.getString("name")
                    val createdAt = folderObj.optLong("createdAt", System.currentTimeMillis())

                    val tracksList = mutableListOf<AudioTrack>()
                    val tracksArray = folderObj.optJSONArray("tracks")
                    if (tracksArray != null) {
                        for (j in 0 until tracksArray.length()) {
                            val trackObj = tracksArray.getJSONObject(j)
                            val uriStr = trackObj.getString("uri")
                            tracksList.add(
                                AudioTrack(
                                    id = trackObj.optLong("id", 0L),
                                    uri = Uri.parse(uriStr),
                                    title = trackObj.optString("title", "Audio Track"),
                                    artist = trackObj.optString("artist", "Unknown Artist"),
                                    album = trackObj.optString("album", ""),
                                    durationMs = trackObj.optLong("durationMs", 0L),
                                    folderName = name,
                                    albumId = trackObj.optLong("albumId", 0L)
                                )
                            )
                        }
                    }

                    list.add(
                        UserFolder(
                            id = id,
                            name = name,
                            createdAt = createdAt,
                            tracks = tracksList
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Error loading folders from persistent storage", e)
                emptyList()
            }
        }
    }

    // --- FOLDER RESUME POSITIONS PERSISTENCE ---

    fun saveFolderPositions(positions: Map<String, FolderPositionEntity>) {
        synchronized(lock) {
            try {
                val jsonArray = JSONArray()
                for ((_, entity) in positions) {
                    val obj = JSONObject()
                    obj.put("folderKey", entity.folderKey)
                    obj.put("trackUri", entity.trackUri)
                    obj.put("trackTitle", entity.trackTitle)
                    obj.put("positionMs", entity.positionMs)
                    obj.put("updatedAt", entity.updatedAt)
                    jsonArray.put(obj)
                }
                writeStringToFileAtomically(folderPositionsFile, jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving folder positions to persistent storage", e)
            }
        }
    }

    fun loadFolderPositions(): List<FolderPositionEntity> {
        synchronized(lock) {
            if (!folderPositionsFile.exists()) return emptyList()
            return try {
                val content = folderPositionsFile.readText()
                if (content.isBlank()) return emptyList()
                val jsonArray = JSONArray(content)
                val list = mutableListOf<FolderPositionEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        FolderPositionEntity(
                            folderKey = obj.getString("folderKey"),
                            trackUri = obj.getString("trackUri"),
                            trackTitle = obj.optString("trackTitle", ""),
                            positionMs = obj.optLong("positionMs", 0L),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Error loading folder positions from persistent storage", e)
                emptyList()
            }
        }
    }

    // --- TRACK POSITIONS PERSISTENCE ---

    fun saveTrackPositions(positions: Map<String, Long>) {
        synchronized(lock) {
            try {
                val jsonObject = JSONObject()
                for ((uri, pos) in positions) {
                    jsonObject.put(uri, pos)
                }
                writeStringToFileAtomically(trackPositionsFile, jsonObject.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving track positions to persistent storage", e)
            }
        }
    }

    fun loadTrackPositions(): Map<String, Long> {
        synchronized(lock) {
            if (!trackPositionsFile.exists()) return emptyMap()
            return try {
                val content = trackPositionsFile.readText()
                if (content.isBlank()) return emptyMap()
                val jsonObject = JSONObject(content)
                val map = mutableMapOf<String, Long>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.getLong(key)
                }
                map
            } catch (e: Exception) {
                Log.e(TAG, "Error loading track positions from persistent storage", e)
                emptyMap()
            }
        }
    }

    // --- CUSTOM TRACK NAMES PERSISTENCE ---

    fun saveCustomNames(names: Map<String, String>) {
        synchronized(lock) {
            try {
                val jsonObject = JSONObject()
                for ((uri, name) in names) {
                    jsonObject.put(uri, name)
                }
                writeStringToFileAtomically(customNamesFile, jsonObject.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving custom track names to persistent storage", e)
            }
        }
    }

    fun loadCustomNames(): Map<String, String> {
        synchronized(lock) {
            if (!customNamesFile.exists()) return emptyMap()
            return try {
                val content = customNamesFile.readText()
                if (content.isBlank()) return emptyMap()
                val jsonObject = JSONObject(content)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.getString(key)
                }
                map
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom track names from persistent storage", e)
                emptyMap()
            }
        }
    }

    // --- Atomic File Write ---
    private fun writeStringToFileAtomically(targetFile: File, content: String) {
        val tempFile = File(context.filesDir, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    companion object {
        private const val TAG = "PersistentStorageMgr"
    }
}
