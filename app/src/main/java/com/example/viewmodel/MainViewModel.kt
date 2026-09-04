package com.example.viewmodel

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LoopCountApp
import com.example.data.repository.AudioRepository
import com.example.data.repository.DeleteResult
import com.example.model.AudioTrack
import com.example.model.DeviceFolder
import com.example.model.UserFolder
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import com.example.ui.theme.ThemeAccent
import com.example.ui.theme.ThemeMode
import com.example.util.toProperTitleCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val allTracks: List<AudioTrack> = emptyList(),
    val deviceFolders: List<DeviceFolder> = emptyList(),
    val userFolders: List<UserFolder> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0 = All Tracks, 1 = My Folders, 2 = Device Folders
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: ThemeAccent = ThemeAccent.PURPLE,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LoopCountApp
    private val repository: AudioRepository = app.repository
    val playerManager: AudioPlayerManager = app.playerManager
    val transferManager = app.transferManager

    val playbackState: StateFlow<PlaybackState> = playerManager.state
    val receiverState = transferManager.receiverState
    val senderProgress = transferManager.senderProgress

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Restore from persistent file storage if needed and keep synced
        viewModelScope.launch {
            repository.restoreAndSyncPersistentStorage()
        }

        // Collect custom folders
        viewModelScope.launch {
            repository.getUserFolders().collect { folders ->
                _uiState.update { it.copy(userFolders = folders) }
            }
        }

        // Collect settings
        viewModelScope.launch {
            repository.getSettings().collect { settings ->
                if (settings != null) {
                    val mode = try {
                        ThemeMode.valueOf(settings.themeMode)
                    } catch (e: Exception) {
                        ThemeMode.SYSTEM
                    }
                    val accent = try {
                        ThemeAccent.valueOf(settings.accentColor)
                    } catch (e: Exception) {
                        ThemeAccent.PURPLE
                    }
                    _uiState.update {
                        it.copy(themeMode = mode, accentColor = accent)
                    }
                }
            }
        }

        // Initialize tracks and built-in demo tracks immediately
        refreshTracks()
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted) {
            refreshTracks()
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refreshTracks(showFeedback: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val tracks = repository.loadDeviceAudioTracks()
                val folders = repository.groupIntoDeviceFolders(tracks)
                _uiState.update {
                    it.copy(
                        allTracks = tracks,
                        deviceFolders = folders,
                        isLoading = false,
                        message = if (showFeedback) {
                            if (tracks.isEmpty()) "Scan completed • No audio files found"
                            else "Library refreshed • ${tracks.size} audio tracks"
                        } else it.message
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Failed to load audio: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // --- Search Filtering ---
    fun getFilteredTracks(): List<AudioTrack> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val all = _uiState.value.allTracks
        if (query.isBlank()) return all
        return all.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.folderName.lowercase().contains(query)
        }
    }

    fun getFilteredUserFolders(): List<UserFolder> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val all = _uiState.value.userFolders
        if (query.isBlank()) return all
        return all.filter { it.name.lowercase().contains(query) }
    }

    fun getFilteredDeviceFolders(): List<DeviceFolder> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val all = _uiState.value.deviceFolders
        if (query.isBlank()) return all
        return all.filter { it.name.lowercase().contains(query) }
    }

    // --- Track Contextual Actions ---
    fun playTrack(track: AudioTrack, queue: List<AudioTrack>) {
        playerManager.playTrack(track, queue)
    }

    fun resumeTrack(track: AudioTrack, queue: List<AudioTrack>) {
        playerManager.resumeTrack(track, queue)
    }

    fun setStopAfterCurrentTrack(enabled: Boolean) {
        playerManager.setStopAfterCurrentTrack(enabled)
        _uiState.update { it.copy(message = "Playback will stop after current track finishes") }
    }

    fun renameTrack(track: AudioTrack, newTitle: String) {
        val trimmed = newTitle.toProperTitleCase()
        if (trimmed.isBlank()) return

        // 1. Optimistic UI update
        _uiState.update { state ->
            val updatedTracks = state.allTracks.map {
                if (it.uri == track.uri) it.copy(title = trimmed) else it
            }
            state.copy(
                allTracks = updatedTracks,
                deviceFolders = repository.groupIntoDeviceFolders(updatedTracks),
                message = "Renamed to \"$trimmed\""
            )
        }

        viewModelScope.launch {
            val success = repository.renameTrack(track, trimmed)
            if (success) {
                refreshTracks()
            }
        }
    }

    fun deleteTrack(track: AudioTrack, onConsentRequired: (IntentSender) -> Unit) {
        // If current playing track is deleted, advance or stop
        if (playerManager.state.value.currentTrack?.uri == track.uri) {
            playerManager.pause()
        }

        viewModelScope.launch {
            when (val result = repository.deleteTrack(track)) {
                is DeleteResult.Success -> {
                    // Optimistically remove from state immediately
                    _uiState.update { state ->
                        val remaining = state.allTracks.filter { it.uri != track.uri }
                        state.copy(
                            allTracks = remaining,
                            deviceFolders = repository.groupIntoDeviceFolders(remaining),
                            message = "Deleted \"${track.displayTitle}\""
                        )
                    }
                    refreshTracks()
                }
                is DeleteResult.RequiresUserConsent -> {
                    onConsentRequired(result.intentSender)
                }
                is DeleteResult.Error -> {
                    _uiState.update { it.copy(message = "Deletion failed: ${result.message}") }
                }
            }
        }
    }

    fun onTrackConsentDeleted(track: AudioTrack) {
        viewModelScope.launch {
            if (playerManager.state.value.currentTrack?.uri == track.uri) {
                playerManager.pause()
            }
            repository.cleanupDeletedTrack(track.uri.toString())
            _uiState.update { state ->
                val remaining = state.allTracks.filter { it.uri != track.uri }
                state.copy(
                    allTracks = remaining,
                    deviceFolders = repository.groupIntoDeviceFolders(remaining),
                    message = "Deleted \"${track.displayTitle}\""
                )
            }
            refreshTracks()
        }
    }

    fun deleteMultipleTracks(tracks: List<AudioTrack>, onConsentRequired: (IntentSender) -> Unit) {
        if (tracks.isEmpty()) return
        val currentPlayingUri = playerManager.state.value.currentTrack?.uri
        if (tracks.any { it.uri == currentPlayingUri }) {
            playerManager.pause()
        }

        viewModelScope.launch {
            when (val result = repository.deleteMultipleTracks(tracks)) {
                is DeleteResult.Success -> {
                    val trackUris = tracks.map { it.uri.toString() }.toSet()
                    _uiState.update { state ->
                        val remaining = state.allTracks.filter { !trackUris.contains(it.uri.toString()) }
                        state.copy(
                            allTracks = remaining,
                            deviceFolders = repository.groupIntoDeviceFolders(remaining),
                            message = "Deleted ${tracks.size} tracks"
                        )
                    }
                    refreshTracks()
                }
                is DeleteResult.RequiresUserConsent -> {
                    onConsentRequired(result.intentSender)
                }
                is DeleteResult.Error -> {
                    _uiState.update { it.copy(message = "Deletion failed: ${result.message}") }
                }
            }
        }
    }

    // --- Custom Folder Actions ---
    fun createUserFolder(name: String) {
        val formattedName = name.toProperTitleCase()
        if (formattedName.isBlank()) return
        viewModelScope.launch {
            repository.createUserFolder(formattedName)
            _uiState.update { it.copy(message = "Created folder \"$formattedName\"") }
        }
    }

    fun createUserFolderWithTracks(name: String, tracks: List<AudioTrack>) {
        createFolderWithMultipleTracks(name, tracks)
    }

    fun createFolderWithMultipleTracks(name: String, tracks: List<AudioTrack>) {
        val formattedName = name.toProperTitleCase()
        if (formattedName.isBlank()) return
        viewModelScope.launch {
            val folderId = repository.createUserFolder(formattedName)
            if (tracks.isNotEmpty()) {
                repository.addTracksToUserFolder(folderId, tracks)
            }
            _uiState.update { it.copy(message = "Created folder \"$formattedName\" with ${tracks.size} tracks") }
        }
    }

    fun createUserFolderWithTrack(name: String, track: AudioTrack) {
        val formattedName = name.toProperTitleCase()
        if (formattedName.isBlank()) return
        viewModelScope.launch {
            val folderId = repository.createUserFolder(formattedName)
            repository.addTracksToUserFolder(folderId, listOf(track))
            _uiState.update { it.copy(message = "Created folder \"$formattedName\" and added track") }
        }
    }

    fun addTrackToFolder(folderId: Long, track: AudioTrack) {
        viewModelScope.launch {
            repository.addTracksToUserFolder(folderId, listOf(track))
            _uiState.update { it.copy(message = "Added track to folder") }
        }
    }

    fun renameUserFolder(folderId: Long, newName: String) {
        val formattedName = newName.toProperTitleCase()
        if (formattedName.isBlank()) return
        viewModelScope.launch {
            repository.renameUserFolder(folderId, formattedName)
            _uiState.update { it.copy(message = "Folder renamed to \"$formattedName\"") }
        }
    }

    fun deleteUserFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteUserFolder(folderId)
            _uiState.update { it.copy(message = "Folder deleted") }
        }
    }

    fun addTracksToFolder(folderId: Long, tracks: List<AudioTrack>) {
        viewModelScope.launch {
            repository.addTracksToUserFolder(folderId, tracks)
            _uiState.update { it.copy(message = "Added ${tracks.size} tracks to folder") }
        }
    }

    fun removeTrackFromFolder(folderId: Long, trackUri: String) {
        viewModelScope.launch {
            repository.removeTrackFromUserFolder(folderId, trackUri)
        }
    }

    fun removeMultipleTracksFromFolder(folderId: Long, trackUris: List<String>) {
        viewModelScope.launch {
            repository.removeMultipleTracksFromUserFolder(folderId, trackUris)
            _uiState.update { it.copy(message = "Removed ${trackUris.size} tracks from folder") }
        }
    }

    fun reorderFolderTracks(folderId: Long, reorderedList: List<AudioTrack>) {
        viewModelScope.launch {
            repository.updateFolderTrackOrder(folderId, reorderedList)
        }
    }

    // --- Play Folder with optional timer and optional shuffle ---
    fun playFolder(folderKey: String? = null, tracks: List<AudioTrack>, timerMinutes: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) {
            _uiState.update { it.copy(message = "This folder has no audio tracks") }
            return
        }
        val playQueue = if (shuffle) tracks.shuffled() else tracks
        if (timerMinutes > 0) {
            playerManager.startFolderTimer(timerMinutes)
            _uiState.update { it.copy(message = if (shuffle) "Playing folder shuffled for $timerMinutes minutes" else "Playing folder for $timerMinutes minutes") }
        } else if (shuffle) {
            _uiState.update { it.copy(message = "Playing folder in random shuffle mode") }
        }
        playerManager.playTrack(playQueue.first(), playQueue, 0L, folderKey = folderKey)
    }

    fun resumeFolder(folderKey: String, tracks: List<AudioTrack>, timerMinutes: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) {
            _uiState.update { it.copy(message = "This folder has no audio tracks") }
            return
        }
        viewModelScope.launch {
            val history = repository.getFolderPosition(folderKey)
            val trackName = history?.trackTitle?.ifBlank { null }
            if (trackName != null) {
                _uiState.update { it.copy(message = "Resuming \"$trackName\"") }
            } else {
                _uiState.update { it.copy(message = "Resuming folder playback") }
            }
        }
        playerManager.resumeFolder(folderKey, tracks, timerMinutes, shuffle)
    }

    fun playMagicRemix(folderName: String, tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) {
            _uiState.update { it.copy(message = "This folder has no audio tracks for Magic Remix") }
            return
        }
        _uiState.update { it.copy(message = "✨ Magic Remix started! Continuous mashup mode active") }
        playerManager.playMagicRemix(folderName, tracks)
    }

    suspend fun getSavedTrackPosition(trackUri: String): Long {
        return repository.getSavedPosition(trackUri)
    }

    suspend fun getFolderPosition(folderKey: String): com.example.data.local.FolderPositionEntity? {
        return repository.getFolderPosition(folderKey)
    }

    // --- Settings ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(themeMode = mode) }
            repository.saveSettings(mode.name, _uiState.value.accentColor.name)
        }
    }

    fun setAccentColor(accent: ThemeAccent) {
        viewModelScope.launch {
            _uiState.update { it.copy(accentColor = accent) }
            repository.saveSettings(_uiState.value.themeMode.name, accent.name)
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            app.clearAppCache()
            _uiState.update { it.copy(message = "App cache cleared successfully") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // --- Wi-Fi Transfer Methods ---
    fun startReceiverServer() {
        transferManager.startReceiver()
    }

    fun stopReceiverServer() {
        transferManager.stopReceiver()
    }

    fun onTransferCompleteRefresh() {
        viewModelScope.launch {
            refreshTracks(showFeedback = true)
            _uiState.update { it.copy(message = "Music library updated with received songs!") }
        }
    }
}
