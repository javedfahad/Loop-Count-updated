package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.UserFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsDialog(
    track: AudioTrack,
    userFolders: List<UserFolder> = emptyList(),
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onStopAfterThis: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddToFolder: ((Long) -> Unit)? = null,
    onCreateFolderWithTrack: ((String) -> Unit)? = null
) {
    var isRenaming by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var isPickingFolder by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newFolderText by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf(track.displayTitle) }

    if (isCreatingFolder) {
        AlertDialog(
            onDismissRequest = { isCreatingFolder = false },
            title = { Text("New Custom Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderText,
                    onValueChange = { newFolderText = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Focus Loops, Favorites") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderText.isNotBlank()) {
                            onCreateFolderWithTrack?.invoke(newFolderText.trim())
                            isCreatingFolder = false
                            isPickingFolder = false
                            onDismiss()
                        }
                    },
                    enabled = newFolderText.isNotBlank()
                ) {
                    Text("Create & Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingFolder = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
        return
    }

    if (isPickingFolder) {
        AlertDialog(
            onDismissRequest = { isPickingFolder = false },
            title = { Text("Add Track to Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Create New Folder Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isCreatingFolder = true }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "+ Create New Folder",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (userFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(userFolders) { folder ->
                                val alreadyIn = folder.tracks.any { it.uri == track.uri }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !alreadyIn) {
                                            onAddToFolder?.invoke(folder.id)
                                            isPickingFolder = false
                                            onDismiss()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderSpecial,
                                        contentDescription = null,
                                        tint = if (alreadyIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            fontWeight = FontWeight.Medium,
                                            color = if (alreadyIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (alreadyIn) "Already in folder" else "${folder.tracks.size} tracks",
                                            fontSize = 11.sp,
                                            color = if (alreadyIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isPickingFolder = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
        return
    }

    if (isRenaming) {
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("Rename Audio", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_text_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameText)
                        isRenaming = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("rename_confirm_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenaming = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
        return
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete Audio", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete \"${track.displayTitle}\" from your device? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("delete_confirm_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDelete = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
        return
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.displayArtist} • ${track.formattedDuration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                // Menu items
                OptionMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Play",
                    onClick = {
                        onPlay()
                        onDismiss()
                    },
                    testTag = "option_play"
                )

                OptionMenuItem(
                    icon = Icons.Default.Replay,
                    title = "Resume",
                    onClick = {
                        onResume()
                        onDismiss()
                    },
                    testTag = "option_resume"
                )

                OptionMenuItem(
                    icon = Icons.Default.StopCircle,
                    title = "Stop after this playback",
                    onClick = {
                        onStopAfterThis()
                        onDismiss()
                    },
                    testTag = "option_stop_after"
                )

                OptionMenuItem(
                    icon = Icons.Default.CreateNewFolder,
                    title = "Add to Folder...",
                    onClick = {
                        isPickingFolder = true
                    },
                    testTag = "option_add_to_folder"
                )

                OptionMenuItem(
                    icon = Icons.Default.DriveFileRenameOutline,
                    title = "Rename",
                    onClick = {
                        isRenaming = true
                    },
                    testTag = "option_rename"
                )

                OptionMenuItem(
                    icon = Icons.Default.Delete,
                    title = "Delete",
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = {
                        isConfirmingDelete = true
                    },
                    testTag = "option_delete"
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(4.dp))

                // Cancel Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("option_cancel")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun OptionMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    testTag: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = titleColor
        )
    }
}
