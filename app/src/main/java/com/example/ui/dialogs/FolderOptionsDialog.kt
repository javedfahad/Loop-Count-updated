package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.toProperTitleCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOptionsDialog(
    folderName: String,
    isUserFolder: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit = onPlay,
    resumeSubtitle: String? = null,
    onMagicRemix: (() -> Unit)? = null,
    onPlayFor: (Int) -> Unit, // in minutes
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showTimerDialog by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(folderName) }

    if (showTimerDialog) {
        FolderTimerDialog(
            folderName = folderName,
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                showTimerDialog = false
                onPlayFor(minutes)
                onDismiss()
            }
        )
        return
    }

    if (isRenaming) {
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("Rename Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameText.toProperTitleCase())
                        isRenaming = false
                        onDismiss()
                    }
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
            title = { Text("Delete Folder", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete \"$folderName\"? This removes the custom folder list from LoopCount. Your original audio files on the device will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        onDelete()
                        onDismiss()
                    }
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

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header with compact icon badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isUserFolder) "Custom Folder" else "Device Storage Folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(4.dp))

                // Menu items
                OptionMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Play from start",
                    onClick = {
                        onPlay()
                        onDismiss()
                    },
                    testTag = "folder_opt_play"
                )

                OptionMenuItem(
                    icon = Icons.Default.Replay,
                    title = "Resume where you left",
                    subtitle = resumeSubtitle ?: "Starts from last played track & position",
                    onClick = {
                        onResume()
                        onDismiss()
                    },
                    testTag = "folder_opt_resume"
                )

                if (onMagicRemix != null) {
                    OptionMenuItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Magic Remix ✨",
                        subtitle = "Non-stop DJ mashup cutting through random song drops",
                        onClick = {
                            onMagicRemix()
                            onDismiss()
                        },
                        testTag = "folder_opt_magic_remix"
                    )
                }

                OptionMenuItem(
                    icon = Icons.Default.Timer,
                    title = "Play for...",
                    onClick = {
                        showTimerDialog = true
                    },
                    testTag = "folder_opt_play_for"
                )

                if (isUserFolder) {
                    OptionMenuItem(
                        icon = Icons.Default.DriveFileRenameOutline,
                        title = "Rename",
                        onClick = { isRenaming = true },
                        testTag = "folder_opt_rename"
                    )

                    OptionMenuItem(
                        icon = Icons.Default.Delete,
                        title = "Delete",
                        titleColor = MaterialTheme.colorScheme.error,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { isConfirmingDelete = true },
                        testTag = "folder_opt_delete"
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 0.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderTimerDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit // minutes
) {
    var selectedOption by remember { mutableStateOf(30) } // default 30 min
    var isCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("45") }

    val presetOptions = listOf(
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour",
        120 to "2 hours"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Play Folder for...", fontWeight = FontWeight.Bold)
                Text(
                    text = "Audio will finish naturally after the timer expires, then stop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                presetOptions.forEach { (mins, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isCustom = false
                                selectedOption = mins
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isCustom && selectedOption == mins,
                            onClick = {
                                isCustom = false
                                selectedOption = mins
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Custom option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isCustom = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustom,
                        onClick = { isCustom = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Custom minutes", style = MaterialTheme.typography.bodyLarge)
                }

                if (isCustom) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) {
                                customText = it
                            }
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = if (isCustom) {
                        customText.toIntOrNull()?.coerceAtLeast(1) ?: 30
                    } else {
                        selectedOption
                    }
                    onConfirm(minutes)
                }
            ) {
                Text("Start Timer", fontWeight = FontWeight.Bold)
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
