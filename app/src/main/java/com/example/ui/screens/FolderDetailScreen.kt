package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.UserFolder
import com.example.playback.PlaybackState
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TrackArtwork
import com.example.ui.dialogs.FolderTimerDialog
import com.example.ui.dialogs.TrackOptionsDialog
import com.example.util.toProperTitleCase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    folder: UserFolder,
    allTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    playbackState: PlaybackState? = null,
    onOpenNowPlaying: (() -> Unit)? = null,
    onPlayPause: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayFolder: (List<AudioTrack>, Int, Boolean) -> Unit,
    onResumeFolder: ((List<AudioTrack>) -> Unit)? = null,
    onMagicRemix: ((List<AudioTrack>) -> Unit)? = null,
    onReorder: (List<AudioTrack>) -> Unit,
    onAddTracks: (List<AudioTrack>) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onRemoveMultipleTracks: ((List<String>) -> Unit)? = null,
    onDeleteTracks: ((List<AudioTrack>) -> Unit)? = null,
    onDeleteFolder: ((Long) -> Unit)? = null,
    onRenameFolder: ((Long, String) -> Unit)? = null
) {
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf(folder.name) }
    var showMenu by remember { mutableStateOf(false) }
    var isShuffleSelected by remember { mutableStateOf(false) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<AudioTrack>() }

    // Local tracks list state for instant UI reordering and sync
    var localTracks by remember(folder.tracks) { mutableStateOf(folder.tracks) }

    LaunchedEffect(folder.tracks) {
        localTracks = folder.tracks
    }

    LaunchedEffect(folder.name) {
        renameInputText = folder.name
    }

    if (showTimerDialog) {
        FolderTimerDialog(
            folderName = folder.name,
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                showTimerDialog = false
                onPlayFolder(localTracks, minutes, isShuffleSelected)
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Folder", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete \"${folder.name}\"? This removes the folder list from Loopify Music. Your original audio files on the device will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteFolder?.invoke(folder.id)
                        onBack()
                    },
                    modifier = Modifier.testTag("confirm_delete_folder_button")
                ) {
                    Text("Delete Folder", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedTracks.size} Songs", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (folder.id > 0) "Remove these ${selectedTracks.size} songs from \"${folder.name}\" or delete permanently?"
                    else "Are you sure you want to delete ${selectedTracks.size} selected songs from your device?"
                )
            },
            confirmButton = {
                Row {
                    if (folder.id > 0) {
                        TextButton(
                            onClick = {
                                val uris = selectedTracks.map { it.uri.toString() }
                                val uriSet = uris.toSet()
                                localTracks = localTracks.filter { !uriSet.contains(it.uri.toString()) }
                                onRemoveMultipleTracks?.invoke(uris) ?: run {
                                    uris.forEach { onRemoveTrack(it) }
                                }
                                selectedTracks.clear()
                                isMultiSelectMode = false
                                showBatchDeleteConfirmDialog = false
                            }
                        ) {
                            Text("Remove from Folder")
                        }
                    }
                    TextButton(
                        onClick = {
                            val toDelete = selectedTracks.toList()
                            onDeleteTracks?.invoke(toDelete)
                            selectedTracks.clear()
                            isMultiSelectMode = false
                            showBatchDeleteConfirmDialog = false
                        },
                        modifier = Modifier.testTag("confirm_batch_delete_button")
                    ) {
                        Text("Delete Permanently", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
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

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            onRenameFolder?.invoke(folder.id, renameInputText.toProperTitleCase())
                            showRenameDialog = false
                        }
                    },
                    enabled = renameInputText.isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAddTracksDialog) {
        AddTracksToFolderDialog(
            allTracks = allTracks,
            existingUris = localTracks.map { it.uri.toString() }.toSet(),
            onDismiss = { showAddTracksDialog = false },
            onConfirm = { selected ->
                showAddTracksDialog = false
                if (selected.isNotEmpty()) {
                    localTracks = localTracks + selected
                    onAddTracks(selected)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                // Multi-select top bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedTracks.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedTracks.clear()
                            },
                            modifier = Modifier.testTag("exit_multi_select_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        // Select All / Deselect All
                        IconButton(
                            onClick = {
                                if (selectedTracks.size == localTracks.size) {
                                    selectedTracks.clear()
                                } else {
                                    selectedTracks.clear()
                                    selectedTracks.addAll(localTracks)
                                }
                            },
                            modifier = Modifier.testTag("multi_select_toggle_all")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }

                        // Play Selected
                        if (selectedTracks.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onPlayFolder(selectedTracks.toList(), 0, false)
                                    isMultiSelectMode = false
                                    selectedTracks.clear()
                                },
                                modifier = Modifier.testTag("multi_play_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play selected")
                            }
                        }

                        // Delete / Remove Selected
                        if (selectedTracks.isNotEmpty()) {
                            IconButton(
                                onClick = { showBatchDeleteConfirmDialog = true },
                                modifier = Modifier.testTag("multi_delete_button")
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
                    title = {
                        Column {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (folder.id > 0) "${folder.tracks.size} tracks • Custom Folder" else "${folder.tracks.size} tracks • Device Folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("folder_detail_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showTimerDialog = true },
                            modifier = Modifier.testTag("folder_detail_timer")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Play for timer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("folder_detail_more_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More folder options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Magic Remix")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "Beta",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    onMagicRemix?.invoke(localTracks)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Resume where you left") },
                                leadingIcon = {
                                    Icon(Icons.Default.Replay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    onResumeFolder?.invoke(localTracks)
                                }
                            )
                            if (folder.id > 0) {
                                DropdownMenuItem(
                                    text = { Text("Rename Folder") },
                                    leadingIcon = {
                                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        renameInputText = folder.name
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (folder.id > 0 && !isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { showAddTracksDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("folder_detail_add_tracks_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add or create tracks"
                    )
                }
            }
        },
        bottomBar = {
            if (playbackState?.currentTrack != null && onOpenNowPlaying != null && onPlayPause != null && onNext != null) {
                MiniPlayerBar(
                    playbackState = playbackState,
                    onClick = onOpenNowPlaying,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val screenWidth = maxWidth
            val gridColumns = if (screenWidth >= 600.dp) 2 else 1

            Column(modifier = Modifier.fillMaxSize()) {
                // Action Banner Bento Card (Hidden during multi-select to keep focus clean)
                if (!isMultiSelectMode) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Play All Button
                            Button(
                                onClick = { onPlayFolder(localTracks, 0, isShuffleSelected) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("folder_detail_play_all"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = localTracks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Play",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }

                            // 2. Resume Button
                            FilledTonalButton(
                                onClick = { onResumeFolder?.invoke(localTracks) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("folder_detail_resume"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = localTracks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Replay,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Resume",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }

                            // 3. Shuffle Toggle Button
                            val shuffleContainerColor = if (isShuffleSelected)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                            val shuffleContentColor = if (isShuffleSelected)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                            val shuffleBorder = if (!isShuffleSelected) androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ) else null

                            FilledTonalButton(
                                onClick = { isShuffleSelected = !isShuffleSelected },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("folder_detail_shuffle"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = shuffleContainerColor,
                                    contentColor = shuffleContentColor
                                ),
                                border = shuffleBorder,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = localTracks.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isShuffleSelected)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Shuffle",
                                    fontWeight = if (isShuffleSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }

                            // 4. Play For (Timer) Button
                            OutlinedButton(
                                onClick = { showTimerDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("folder_detail_play_for"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = localTracks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Timer",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Magic Remix (Beta) Banner / Quick Action
                    if (localTracks.isNotEmpty()) {
                        val isRemixActiveForThisFolder = playbackState?.isMagicRemixActive == true &&
                                (playbackState.magicFolderName == folder.name || playbackState.magicFolderName == null)

                        if (isRemixActiveForThisFolder) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("folder_detail_magic_remix_active_banner"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "✨ Magic Remix Active • Cut #${playbackState?.magicTransitionCount ?: 1}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    "Beta",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Next drop in ${playbackState?.magicSliceRemainingSeconds ?: 0}s • Non-stop mashup",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onNext?.invoke() },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Next Cut", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onMagicRemix?.invoke(localTracks) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(bottom = 6.dp)
                                    .testTag("folder_detail_magic_remix_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "✨ Magic Remix (Beta)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "In Dev",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (localTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No tracks in this folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap + at the top or bottom to add songs to this folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddTracksDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Songs")
                            }
                        }
                    }
                } else {
                    // Generous bottom content padding so the bottom player never overlaps the last items!
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = localTracks,
                            key = { _, item -> item.uri.toString() }
                        ) { index, track ->
                            val isCurrent = currentTrack?.uri == track.uri
                            val isSelected = selectedTracks.any { it.uri == track.uri }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                if (isSelected) {
                                                    selectedTracks.removeAll { it.uri == track.uri }
                                                    if (selectedTracks.isEmpty()) isMultiSelectMode = false
                                                } else {
                                                    selectedTracks.add(track)
                                                }
                                            } else {
                                                onPlayTrack(track, localTracks)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isMultiSelectMode) {
                                                isMultiSelectMode = true
                                                selectedTracks.add(track)
                                            }
                                        }
                                    )
                                    .testTag("folder_track_${track.id}"),
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // In multi-select mode, show Checkbox
                                    if (isMultiSelectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (!selectedTracks.any { it.uri == track.uri }) selectedTracks.add(track)
                                                } else {
                                                    selectedTracks.removeAll { it.uri == track.uri }
                                                    if (selectedTracks.isEmpty()) isMultiSelectMode = false
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    } else {
                                        // Artwork thumbnail
                                        TrackArtwork(
                                            track = track,
                                            isPlaying = isCurrent && playbackState?.isPlaying == true,
                                            shape = RoundedCornerShape(10.dp),
                                            iconSize = 18.dp,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    // Track title & artist
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.displayArtist} • ${track.formattedDuration}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // If not in multi-select mode, show reorder & quick delete buttons
                                    if (!isMultiSelectMode) {
                                        // Reorder buttons (Move Up / Move Down)
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val mutable = localTracks.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index - 1]
                                                    mutable[index - 1] = temp
                                                    localTracks = mutable
                                                    onReorder(mutable)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Move up",
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index < localTracks.size - 1) {
                                                    val mutable = localTracks.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index + 1]
                                                    mutable[index + 1] = temp
                                                    localTracks = mutable
                                                    onReorder(mutable)
                                                }
                                            },
                                            enabled = index < localTracks.size - 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Move down",
                                                tint = if (index < localTracks.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Delete from folder
                                        IconButton(
                                            onClick = {
                                                localTracks = localTracks.filter { it.uri != track.uri }
                                                onRemoveTrack(track.uri.toString())
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove from folder",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            // Extra space so user can scroll above mini-player
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTracksToFolderDialog(
    allTracks: List<AudioTrack>,
    existingUris: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<AudioTrack>) -> Unit
) {
    val selectedTracks = remember { mutableStateListOf<AudioTrack>() }
    var searchQuery by remember { mutableStateOf("") }

    val availableTracks = remember(allTracks, existingUris) {
        allTracks.filter { !existingUris.contains(it.uri.toString()) }
    }

    val filteredList = remember(availableTracks, searchQuery) {
        if (searchQuery.isBlank()) availableTracks
        else availableTracks.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
                    it.displayArtist.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Songs to Folder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} available",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            if (selectedTracks.size == filteredList.size) {
                                selectedTracks.clear()
                            } else {
                                selectedTracks.clear()
                                selectedTracks.addAll(filteredList)
                            }
                        }
                    ) {
                        Text(if (selectedTracks.size == filteredList.size) "Deselect All" else "Select All")
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching songs" else "All songs are already in this folder",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList.size) { i ->
                            val track = filteredList[i]
                            val isSelected = selectedTracks.any { it.uri == track.uri }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (isSelected) selectedTracks.removeAll { it.uri == track.uri }
                                        else selectedTracks.add(track)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedTracks.any { it.uri == track.uri }) selectedTracks.add(track)
                                        } else {
                                            selectedTracks.removeAll { it.uri == track.uri }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.displayTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.displayArtist} • ${track.formattedDuration}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTracks.toList()) },
                enabled = selectedTracks.isNotEmpty()
            ) {
                Text("Add (${selectedTracks.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
