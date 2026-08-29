package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.UserFolder
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import com.example.ui.components.FolderItemCard
import com.example.ui.components.LoopCountLogo
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TrackItemCard
import com.example.ui.dialogs.FolderOptionsDialog
import com.example.ui.dialogs.RepeatCountDialog
import com.example.ui.dialogs.TrackOptionsDialog
import kotlinx.coroutines.launch

enum class SortMode {
    TITLE, ARTIST, DURATION, DATE_ADDED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tracks: List<AudioTrack>,
    userFolders: List<UserFolder>,
    deviceFolders: Map<String, List<AudioTrack>>,
    playbackState: PlaybackState,
    playerManager: AudioPlayerManager,
    isLoading: Boolean = false,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshTracks: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenFolderDetail: (UserFolder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onRenameTrack: (AudioTrack, String) -> Unit,
    onDeleteTrack: (AudioTrack) -> Unit,
    onAddTrackToFolder: (Long, AudioTrack) -> Unit = { _, _ -> },
    onCreateFolderWithTrack: (String, AudioTrack) -> Unit = { _, _ -> }
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val allAudiosGridState = rememberLazyGridState()
    val foldersGridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Memoized sorted device folders list to avoid main-thread map iteration on recomposition
    val sortedDeviceFolderList = remember(deviceFolders) {
        deviceFolders.toList().sortedBy { it.first }
    }

    val filteredUserFolders = remember(userFolders, searchQuery) {
        if (searchQuery.isBlank()) userFolders
        else userFolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredDeviceFolderList = remember(sortedDeviceFolderList, searchQuery) {
        if (searchQuery.isBlank()) sortedDeviceFolderList
        else sortedDeviceFolderList.filter { (folderName, _) ->
            folderName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Dialog states
    var selectedTrackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForRepeatDialog by remember { mutableStateOf<AudioTrack?>(null) }
    var selectedFolderForOptions by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // Name, isUserFolder
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, searchQuery, sortMode) {
        val filtered = if (searchQuery.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.displayTitle.contains(searchQuery, ignoreCase = true) ||
                        it.displayArtist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortMode) {
            SortMode.TITLE -> filtered.sortedBy { it.displayTitle.lowercase() }
            SortMode.ARTIST -> filtered.sortedBy { it.displayArtist.lowercase() }
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortMode.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        }
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Custom Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Study Loops, Mantras, Workout") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_folder_text_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim())
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    },
                    enabled = newFolderName.isNotBlank(),
                    modifier = Modifier.testTag("create_folder_confirm_button")
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Track options dialog
    selectedTrackForOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            userFolders = userFolders,
            onDismiss = { selectedTrackForOptions = null },
            onPlay = {
                playerManager.playTrack(track, filteredTracks, startPositionMs = 0L)
            },
            onResume = {
                playerManager.resumeTrack(track, filteredTracks)
            },
            onRepeatOptions = {
                trackForRepeatDialog = track
            },
            onStopAfterThis = {
                val isCurrentPlayingTrack = playbackState.currentTrack?.uri == track.uri
                if (isCurrentPlayingTrack) {
                    playerManager.setStopAfterCurrentTrack(true)
                } else {
                    playerManager.playTrack(track, filteredTracks, startPositionMs = 0L)
                    playerManager.setStopAfterCurrentTrack(true)
                }
            },
            onRename = { newTitle ->
                onRenameTrack(track, newTitle)
            },
            onDelete = {
                onDeleteTrack(track)
            },
            onAddToFolder = { folderId ->
                onAddTrackToFolder(folderId, track)
            },
            onCreateFolderWithTrack = { folderName ->
                onCreateFolderWithTrack(folderName, track)
            }
        )
    }

    // Repeat & Stop Count Dialog from 3-dot menu or track selection
    trackForRepeatDialog?.let { track ->
        val isCurrentPlayingTrack = playbackState.currentTrack?.uri == track.uri
        RepeatCountDialog(
            initialCount = if (isCurrentPlayingTrack) playbackState.repeatCountTotal else 0,
            initialStopAfterFinish = if (isCurrentPlayingTrack) playbackState.stopAfterFinish else false,
            onDismiss = { trackForRepeatDialog = null },
            onStartRepeat = { count, stopAfterFinish ->
                if (isCurrentPlayingTrack) {
                    playerManager.setRepeatCount(count, stopAfterFinish)
                } else {
                    playerManager.playTrack(track, filteredTracks, startPositionMs = 0L)
                    playerManager.setRepeatCount(count, stopAfterFinish)
                }
                trackForRepeatDialog = null
            }
        )
    }

    // Folder options dialog
    selectedFolderForOptions?.let { (folderName, isUserFolder) ->
        val userFolderObj = userFolders.find { it.name == folderName }
        val tracksInFolder = if (isUserFolder) {
            userFolderObj?.tracks ?: emptyList()
        } else {
            deviceFolders[folderName] ?: emptyList()
        }
        val folderKey = if (isUserFolder) "user_${userFolderObj?.id}" else "device_$folderName"

        FolderOptionsDialog(
            folderName = folderName,
            isUserFolder = isUserFolder,
            onDismiss = { selectedFolderForOptions = null },
            onPlay = {
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.playTrack(tracksInFolder.first(), tracksInFolder, startPositionMs = 0L, folderKey = folderKey)
                }
            },
            onResume = {
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.resumeFolder(folderKey, tracksInFolder)
                }
            },
            onPlayFor = { minutes ->
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.playTrack(tracksInFolder.first(), tracksInFolder, startPositionMs = 0L, folderKey = folderKey)
                    playerManager.startFolderTimer(minutes)
                }
            },
            onRename = { newName ->
                userFolderObj?.let { onRenameFolder(it.id, newName) }
            },
            onDelete = {
                userFolderObj?.let { onDeleteFolder(it.id) }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("nav_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation drawer"
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoopCountLogo(size = 28.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LoopCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }

                // Sleek, compact segmented pill tabs (eliminates gap)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val allAudiosSelected = selectedTabIndex == 0
                        Surface(
                            onClick = { selectedTabIndex = 0 },
                            shape = RoundedCornerShape(11.dp),
                            color = if (allAudiosSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("tab_all_audios")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "All Audios (${filteredTracks.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (allAudiosSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (allAudiosSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val foldersSelected = selectedTabIndex == 1
                        val totalFolders = userFolders.size + deviceFolders.size
                        Surface(
                            onClick = { selectedTabIndex = 1 },
                            shape = RoundedCornerShape(11.dp),
                            color = if (foldersSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("tab_folders")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Folders ($totalFolders)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (foldersSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (foldersSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // When music plays, the mini player smoothly slides up ABOVE the bottom dock
                AnimatedVisibility(
                    visible = playbackState.currentTrack != null,
                    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                        animationSpec = tween(200),
                        initialOffsetY = { it }
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                        animationSpec = tween(150),
                        targetOffsetY = { it }
                    )
                ) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        onClick = onOpenNowPlaying,
                        onPlayPause = { playerManager.togglePlayPause() },
                        onNext = { playerManager.next() },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Permanent Bottom Dock (Search, Filter/Sort, Refresh & New Folder)
                BottomOneHandedDock(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    isLoading = isLoading,
                    onRefresh = onRefreshTracks,
                    selectedTabIndex = selectedTabIndex,
                    onCreateFolderClick = { showCreateFolderDialog = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val screenWidth = maxWidth
            val gridColumns = when {
                screenWidth >= 840.dp -> 3
                screenWidth >= 600.dp -> 2
                else -> 1
            }

            if (selectedTabIndex == 0) {
                // All Audios Tab
                if (filteredTracks.isEmpty()) {
                    if (!hasStoragePermission) {
                        PermissionRequestBanner(
                            onRequestPermission = onRequestPermission,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    } else {
                        EmptyTracksPlaceholder(
                            isSearching = searchQuery.isNotBlank(),
                            onRefresh = onRefreshTracks,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = allAudiosGridState,
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasStoragePermission) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CompactPermissionCard(
                                    onRequestPermission = onRequestPermission,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        items(
                            items = filteredTracks,
                            key = { it.id }
                        ) { track ->
                            val isCurrent = playbackState.currentTrack?.id == track.id
                            TrackItemCard(
                                track = track,
                                isPlaying = playbackState.isPlaying,
                                isCurrentTrack = isCurrent,
                                onClick = {
                                    playerManager.playTrack(track, filteredTracks)
                                },
                                onLongClick = {
                                    selectedTrackForOptions = track
                                },
                                onOptionsClick = {
                                    selectedTrackForOptions = track
                                }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            } else {
                // Folders Tab
                LazyVerticalGrid(
                    state = foldersGridState,
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Custom Folders Section
                    if (filteredUserFolders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "CUSTOM FOLDERS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(
                            items = filteredUserFolders,
                            key = { "user_${it.id}" }
                        ) { folder ->
                            FolderItemCard(
                                name = folder.name,
                                trackCount = folder.tracks.size,
                                isUserFolder = true,
                                onClick = {
                                    onOpenFolderDetail(folder)
                                },
                                onLongClick = {
                                    selectedFolderForOptions = folder.name to true
                                },
                                onOptionsClick = {
                                    selectedFolderForOptions = folder.name to true
                                },
                                onQuickPlayClick = {
                                    if (folder.tracks.isNotEmpty()) {
                                        playerManager.playTrack(folder.tracks.first(), folder.tracks)
                                    }
                                }
                            )
                        }
                    }

                    // Device Folders Section
                    if (filteredDeviceFolderList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "DEVICE STORAGE FOLDERS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(
                            items = filteredDeviceFolderList,
                            key = { "device_${it.first}" }
                        ) { (folderName, folderTracks) ->
                            FolderItemCard(
                                name = folderName,
                                trackCount = folderTracks.size,
                                isUserFolder = false,
                                onClick = {
                                    val syntheticFolder = UserFolder(
                                        id = -1,
                                        name = folderName,
                                        createdAt = System.currentTimeMillis(),
                                        tracks = folderTracks
                                    )
                                    onOpenFolderDetail(syntheticFolder)
                                },
                                onLongClick = {
                                    selectedFolderForOptions = folderName to false
                                },
                                onOptionsClick = {
                                    selectedFolderForOptions = folderName to false
                                },
                                onQuickPlayClick = {
                                    if (folderTracks.isNotEmpty()) {
                                        playerManager.playTrack(folderTracks.first(), folderTracks)
                                    }
                                }
                            )
                        }
                    }

                    if (filteredUserFolders.isEmpty() && filteredDeviceFolderList.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No matching folders found" else "No audio folders detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Try another search term" else "Tap + button below to create a custom folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio permission",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Audio Permission Needed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "LoopCount needs permission to read audio files stored on your device to enable loop counting and playback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text("Grant Storage Permission", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyTracksPlaceholder(
    isSearching: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSearching) "No matching audio tracks" else "No audio files found on device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearching) "Try a different search term" else "Add MP3, AAC, WAV, or FLAC audio files to your device storage",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("empty_refresh_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Scan Storage")
        }
    }
}

@Composable
fun CompactPermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scan Device Audio",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Grant storage permission to find your local music files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("compact_grant_permission_button")
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RotatingRefreshIconButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    if (isLoading) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
        val refreshRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refresh_angle"
        )
        IconButton(
            onClick = onClick,
            enabled = false,
            modifier = Modifier
                .graphicsLayer { rotationZ = refreshRotation }
                .testTag("refresh_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refreshing...",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.testTag("refresh_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh library",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BottomOneHandedDock(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    selectedTabIndex: Int,
    onCreateFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSearchActive) {
                IconButton(
                    onClick = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    },
                    modifier = Modifier.testTag("search_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = if (selectedTabIndex == 0) "Search audios..." else "Search folders...",
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_text_field")
                )
            } else {
                Surface(
                    onClick = { onSearchActiveChange(true) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("search_toggle_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) searchQuery else if (selectedTabIndex == 0) "Search audio files..." else "Search folders...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("sort_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SortByAlpha,
                            contentDescription = "Sort tracks",
                            tint = if (sortMode != SortMode.TITLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Title") },
                            trailingIcon = {
                                if (sortMode == SortMode.TITLE) Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSortModeChange(SortMode.TITLE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Artist") },
                            trailingIcon = {
                                if (sortMode == SortMode.ARTIST) Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSortModeChange(SortMode.ARTIST)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Duration") },
                            trailingIcon = {
                                if (sortMode == SortMode.DURATION) Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSortModeChange(SortMode.DURATION)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Recently Added") },
                            trailingIcon = {
                                if (sortMode == SortMode.DATE_ADDED) Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSortModeChange(SortMode.DATE_ADDED)
                                showSortMenu = false
                            }
                        )
                    }
                }

                RotatingRefreshIconButton(
                    isLoading = isLoading,
                    onClick = onRefresh
                )

                if (selectedTabIndex == 1) {
                    IconButton(
                        onClick = onCreateFolderClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .testTag("create_folder_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Create new folder",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


