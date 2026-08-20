package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Infinite rotation for loading state
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

    // Auto-refresh when entering Folders tab
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1) {
            onRefreshTracks()
        }
    }

    // Dialog states
    var selectedTrackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
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
                playerManager.playTrack(track, filteredTracks)
            },
            onResume = {
                playerManager.playTrack(track, filteredTracks)
            },
            onStopAfterThis = {
                playerManager.setStopAfterCurrentTrack(true)
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

    // Folder options dialog
    selectedFolderForOptions?.let { (folderName, isUserFolder) ->
        val userFolderObj = userFolders.find { it.name == folderName }
        val tracksInFolder = if (isUserFolder) {
            userFolderObj?.tracks ?: emptyList()
        } else {
            deviceFolders[folderName] ?: emptyList()
        }

        FolderOptionsDialog(
            folderName = folderName,
            isUserFolder = isUserFolder,
            onDismiss = { selectedFolderForOptions = null },
            onPlay = {
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.playTrack(tracksInFolder.first(), tracksInFolder)
                }
            },
            onPlayFor = { minutes ->
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.playTrack(tracksInFolder.first(), tracksInFolder)
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
                if (isSearchActive) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.testTag("search_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit search"
                                )
                            }
                        },
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search audio files, folders...", fontSize = 14.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_text_field")
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                } else {
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
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                LoopCountLogo(size = 30.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LoopCount",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("search_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SortByAlpha,
                                    contentDescription = "Sort tracks"
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort by Title") },
                                    onClick = {
                                        sortMode = SortMode.TITLE
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Artist") },
                                    onClick = {
                                        sortMode = SortMode.ARTIST
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Duration") },
                                    onClick = {
                                        sortMode = SortMode.DURATION
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Recently Added") },
                                    onClick = {
                                        sortMode = SortMode.DATE_ADDED
                                        showSortMenu = false
                                    }
                                )
                            }

                            if (selectedTabIndex == 0) {
                                IconButton(
                                    onClick = onRefreshTracks,
                                    enabled = !isLoading,
                                    modifier = Modifier.testTag("refresh_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh library",
                                        modifier = if (isLoading) Modifier.graphicsLayer(rotationZ = refreshRotation) else Modifier
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }

                // Tabs: All Audios & Folders
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                text = "All Audios (${filteredTracks.size})",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_all_audios")
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            val totalFolders = userFolders.size + deviceFolders.size
                            Text(
                                text = "Folders ($totalFolders)",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_folders")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("create_folder_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Create new folder"
                    )
                }
            }
        },
        bottomBar = {
            if (playbackState.currentTrack != null) {
                MiniPlayerBar(
                    playbackState = playbackState,
                    onClick = onOpenNowPlaying,
                    onPlayPause = { playerManager.togglePlayPause() },
                    onNext = { playerManager.next() }
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
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                // Folders Tab
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Custom Folders Section
                    if (userFolders.isNotEmpty()) {
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
                            items = userFolders,
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
                    if (deviceFolders.isNotEmpty()) {
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

                        val sortedDeviceFolderList = deviceFolders.toList().sortedBy { it.first }
                        items(
                            items = sortedDeviceFolderList,
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

                    if (userFolders.isEmpty() && deviceFolders.isEmpty()) {
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
                                        text = "No audio folders detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap + button to create a custom playlist folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(80.dp))
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

