package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.UserFolder
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import com.example.ui.components.FolderItemCard
import com.example.ui.components.LoopifyLogo
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TrackItemCard
import com.example.ui.dialogs.FolderOptionsDialog
import com.example.ui.dialogs.RepeatCountDialog
import com.example.ui.dialogs.TrackOptionsDialog
import com.example.util.toProperTitleCase
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
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
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
    onDeleteMultipleTracks: ((List<AudioTrack>) -> Unit)? = null,
    onAddTrackToFolder: (Long, AudioTrack) -> Unit = { _, _ -> },
    onAddMultipleTracksToFolder: ((Long, List<AudioTrack>) -> Unit)? = null,
    onCreateFolderWithTrack: (String, AudioTrack) -> Unit = { _, _ -> },
    onCreateFolderWithMultipleTracks: ((String, List<AudioTrack>) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = selectedTab.coerceIn(0, 1), pageCount = { 2 })
    val selectedTabIndex = pagerState.currentPage
    val allAudiosGridState = rememberLazyGridState()
    val foldersGridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-select state
    var isMultiSelectMode by rememberSaveable { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<AudioTrack>() }

    // Dialog states
    var showBatchAddToFolderDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab && (selectedTab == 0 || selectedTab == 1)) {
            pagerState.scrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onTabSelected(pagerState.currentPage)
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedTracks.clear()
        }
    }

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

    // Dialog states for single item
    var selectedTrackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForRepeatDialog by remember { mutableStateOf<AudioTrack?>(null) }
    var selectedFolderForOptions by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // Name, isUserFolder
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Intercept back button when in multi-select mode or search mode
    BackHandler(enabled = isMultiSelectMode || isSearchActive || searchQuery.isNotEmpty()) {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedTracks.clear()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            isSearchActive = false
            searchQuery = ""
        }
    }

    // When swiping tabs, dismiss active search and reset to normal view
    LaunchedEffect(pagerState.currentPage) {
        if (isSearchActive || searchQuery.isNotEmpty()) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            isSearchActive = false
            searchQuery = ""
        }
    }

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
            SortMode.TITLE -> filtered.sortedWith(
                compareBy<AudioTrack> { it.displayTitle.lowercase() }
                    .thenBy { it.displayArtist.lowercase() }
                    .thenByDescending { it.id }
            )
            SortMode.ARTIST -> filtered.sortedWith(
                compareBy<AudioTrack> { it.displayArtist.lowercase() }
                    .thenBy { it.displayTitle.lowercase() }
                    .thenByDescending { it.id }
            )
            SortMode.DURATION -> filtered.sortedWith(
                compareByDescending<AudioTrack> { it.durationMs }
                    .thenBy { it.displayTitle.lowercase() }
                    .thenByDescending { it.id }
            )
            SortMode.DATE_ADDED -> filtered.sortedWith(
                compareByDescending<AudioTrack> { it.dateAdded }
                    .thenByDescending { it.id }
                    .thenBy { it.displayTitle.lowercase() }
            )
        }
    }

    // Scroll smoothly back to top whenever user changes sort or search
    LaunchedEffect(sortMode) {
        if (filteredTracks.isNotEmpty()) {
            allAudiosGridState.scrollToItem(0)
        }
    }
    LaunchedEffect(searchQuery) {
        if (filteredTracks.isNotEmpty()) {
            allAudiosGridState.scrollToItem(0)
        }
    }

    // Batch Add To Folder Dialog
    if (showBatchAddToFolderDialog) {
        var createNewInBatch by remember { mutableStateOf(false) }
        var batchFolderName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showBatchAddToFolderDialog = false },
            title = { Text("Add ${selectedTracks.size} Songs to Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (createNewInBatch) {
                        OutlinedTextField(
                            value = batchFolderName,
                            onValueChange = { batchFolderName = it },
                            label = { Text("New Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Select a folder or create a new one:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { createNewInBatch = true }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("+ Create New Folder", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            items(userFolders.size) { idx ->
                                val uf = userFolders[idx]
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val toAdd = selectedTracks.toList()
                                            onAddMultipleTracksToFolder?.invoke(uf.id, toAdd) ?: run {
                                                toAdd.forEach { onAddTrackToFolder(uf.id, it) }
                                            }
                                            showBatchAddToFolderDialog = false
                                            isMultiSelectMode = false
                                            selectedTracks.clear()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(uf.name, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("${uf.tracks.size} tracks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (createNewInBatch) {
                    TextButton(
                        onClick = {
                            if (batchFolderName.isNotBlank()) {
                                val toAdd = selectedTracks.toList()
                                onCreateFolderWithMultipleTracks?.invoke(batchFolderName.toProperTitleCase(), toAdd) ?: run {
                                    onCreateFolder(batchFolderName.toProperTitleCase())
                                }
                                showBatchAddToFolderDialog = false
                                isMultiSelectMode = false
                                selectedTracks.clear()
                            }
                        },
                        enabled = batchFolderName.isNotBlank()
                    ) {
                        Text("Create & Add", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchAddToFolderDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Batch Delete Dialog
    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedTracks.size} Songs", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete ${selectedTracks.size} selected songs? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = selectedTracks.toList()
                        onDeleteMultipleTracks?.invoke(toDelete) ?: run {
                            toDelete.forEach { onDeleteTrack(it) }
                        }
                        showBatchDeleteConfirmDialog = false
                        isMultiSelectMode = false
                        selectedTracks.clear()
                    },
                    modifier = Modifier.testTag("confirm_batch_delete_tracks_btn")
                ) {
                    Text("Delete Songs", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
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
                            onCreateFolder(newFolderName.toProperTitleCase())
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
                onOpenNowPlaying()
            },
            onResume = {
                playerManager.resumeTrack(track, filteredTracks)
                onOpenNowPlaying()
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
            },
            onSelectMultiple = {
                isMultiSelectMode = true
                if (!selectedTracks.any { it.uri == track.uri }) {
                    selectedTracks.add(track)
                }
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
            onMagicRemix = {
                if (tracksInFolder.isNotEmpty()) {
                    playerManager.playMagicRemix(folderName, tracksInFolder)
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
                if (isMultiSelectMode) {
                    // Contextual Multi-Select Bar
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedTracks.clear()
                                },
                                modifier = Modifier.testTag("home_exit_multi_select")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close multi select")
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedTracks.size} Selected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        actions = {
                            // Select All / Deselect All
                            IconButton(
                                onClick = {
                                    if (selectedTracks.size == filteredTracks.size) {
                                        selectedTracks.clear()
                                    } else {
                                        selectedTracks.clear()
                                        selectedTracks.addAll(filteredTracks)
                                    }
                                },
                                modifier = Modifier.testTag("home_select_all_btn")
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all tracks")
                            }

                            // Play Selected
                            if (selectedTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        playerManager.playTrack(selectedTracks.first(), selectedTracks.toList())
                                        onOpenNowPlaying()
                                        isMultiSelectMode = false
                                        selectedTracks.clear()
                                    },
                                    modifier = Modifier.testTag("home_play_selected_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play selected")
                                }
                            }

                            // Add to Folder
                            if (selectedTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { showBatchAddToFolderDialog = true },
                                    modifier = Modifier.testTag("home_add_selected_to_folder_btn")
                                ) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to folder")
                                }
                            }

                            // Delete Selected
                            if (selectedTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { showBatchDeleteConfirmDialog = true },
                                    modifier = Modifier.testTag("home_delete_selected_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete selected",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LoopifyLogo(size = 30.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Loopify Music",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
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
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }

                // Sleek, compact segmented pill tabs (eliminates gap)
                if (!isMultiSelectMode) {
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
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (allAudiosSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("tab_all_audios")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "All Audios",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (allAudiosSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (allAudiosSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val foldersSelected = selectedTabIndex == 1
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (foldersSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("tab_folders")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Folders",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (foldersSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (foldersSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // When music plays, the mini player smoothly slides up ABOVE the bottom dock with comfortable clearance
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
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Permanent Bottom Dock (Search, Filter/Sort, Refresh & New Folder / New Song)
                if (!isMultiSelectMode) {
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
                } else {
                    // Floating Multi-Select Batch Operations Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Select All / Deselect button
                                TextButton(
                                    onClick = {
                                        if (selectedTracks.size == filteredTracks.size) {
                                            selectedTracks.clear()
                                            isMultiSelectMode = false
                                        } else {
                                            selectedTracks.clear()
                                            selectedTracks.addAll(filteredTracks)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("multi_select_all_btn")
                                ) {
                                    Text(
                                        text = if (selectedTracks.size == filteredTracks.size) "Deselect" else "All (${filteredTracks.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Play Selected
                                FilledTonalButton(
                                    onClick = {
                                        if (selectedTracks.isNotEmpty()) {
                                            val listToPlay = selectedTracks.toList()
                                            playerManager.playTrack(listToPlay.first(), listToPlay)
                                            onOpenNowPlaying()
                                            isMultiSelectMode = false
                                            selectedTracks.clear()
                                        }
                                    },
                                    enabled = selectedTracks.isNotEmpty(),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("multi_select_play_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Play (${selectedTracks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Add to Folder
                                Button(
                                    onClick = { showBatchAddToFolderDialog = true },
                                    enabled = selectedTracks.isNotEmpty(),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("multi_select_add_folder_btn")
                                ) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Done / Close Multi-Select
                            IconButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedTracks.clear()
                                },
                                modifier = Modifier.size(36.dp).testTag("multi_select_close_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close multi-select", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    // All Audios Tab
                    if (filteredTracks.isEmpty()) {
                        if (!hasStoragePermission) {
                            PermissionRequestBanner(
                                onRequestPermission = onRequestPermission,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            )
                        } else {
                            EmptyTracksPlaceholder(
                                isSearching = searchQuery.isNotBlank(),
                                onRefresh = onRefreshTracks,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = allAudiosGridState,
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
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

                            // Subheader controls: Track count and Sort indicator chip
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${filteredTracks.size} songs",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        ) {
                                            Text(
                                                text = when (sortMode) {
                                                    SortMode.TITLE -> "Title A-Z"
                                                    SortMode.ARTIST -> "Artist"
                                                    SortMode.DURATION -> "Duration"
                                                    SortMode.DATE_ADDED -> "Recently Added"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = filteredTracks,
                                key = { "${it.uri}_${it.id}" }
                            ) { track ->
                                val isCurrent = playbackState.currentTrack?.uri == track.uri
                                val isSelected = selectedTracks.any { it.uri == track.uri }

                                TrackItemCard(
                                    track = track,
                                    isPlaying = playbackState.isPlaying,
                                    isCurrentTrack = isCurrent,
                                    isSelectionMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected) {
                                                selectedTracks.removeAll { it.uri == track.uri }
                                                if (selectedTracks.isEmpty()) isMultiSelectMode = false
                                            } else {
                                                selectedTracks.add(track)
                                            }
                                        } else {
                                            playerManager.playTrack(track, filteredTracks)
                                            onOpenNowPlaying()
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedTracks.add(track)
                                        }
                                    },
                                    onOptionsClick = {
                                        selectedTrackForOptions = track
                                    },
                                    onSelectionToggle = { checked ->
                                        if (checked) {
                                            if (!selectedTracks.any { it.uri == track.uri }) selectedTracks.add(track)
                                        } else {
                                            selectedTracks.removeAll { it.uri == track.uri }
                                            if (selectedTracks.isEmpty()) isMultiSelectMode = false
                                        }
                                    }
                                )
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(100.dp))
                            }
                        }
                    }
                } else {
                    // Folders Tab
                    LazyVerticalGrid(
                        state = foldersGridState,
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
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
                                EmptyFoldersPlaceholder(
                                    isSearching = searchQuery.isNotBlank(),
                                    onCreateFolder = { showCreateFolderDialog = true }
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
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
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Search else Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = if (isSearching) "No matching songs" else "No audio tracks found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isSearching)
                    "Try searching for another artist, song title, or album"
                else
                    "Scan your device storage for audio files or check app permissions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!isSearching) {
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Storage")
                }
            }
        }
    }
}

@Composable
fun EmptyFoldersPlaceholder(
    isSearching: Boolean,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = if (isSearching) "No matching folders" else "No custom folders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isSearching)
                    "Check folder name spelling or clear search"
                else
                    "Create custom folders to organize your favorite repeat loops and playlists",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!isSearching) {
                Button(
                    onClick = onCreateFolder,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Folder")
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
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Audio Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Grant audio access to load music from your device storage, or use built-in demo loops.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Bold)
            }
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Permission needed for full library",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRequestPermission) {
                Text("Grant", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RotatingRefreshIconButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .testTag("refresh_icon_button")
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                rotationZ = if (isLoading) rotation else 0f
            }
        )
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
    onCreateFolderClick: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = if (selectedTabIndex == 0) "Search songs or artists..." else "Search folders...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                        onSearchActiveChange(false)
                                        onSearchQueryChange("")
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    onSearchActiveChange(false)
                                    onSearchQueryChange("")
                                    true
                                } else false
                            }
                            .testTag("search_text_field")
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
}
