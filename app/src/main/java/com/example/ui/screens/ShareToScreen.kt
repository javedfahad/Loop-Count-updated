package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AudioTrack
import com.example.model.DeviceFolder
import com.example.model.UserFolder
import com.example.transfer.DiscoveredDevice
import com.example.transfer.NetworkUtils
import com.example.transfer.ReceiverState
import com.example.transfer.TransferItem
import com.example.transfer.TransferProgress
import com.example.transfer.WifiTransferManager
import kotlinx.coroutines.launch

enum class ShareMode {
    SEND,
    RECEIVE
}

enum class SelectionTab {
    SONGS,
    FOLDERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToScreen(
    allTracks: List<AudioTrack>,
    userFolders: List<UserFolder>,
    deviceFolders: List<DeviceFolder>,
    transferManager: WifiTransferManager,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var shareMode by remember { mutableStateOf(ShareMode.SEND) }
    val receiverState by transferManager.receiverState.collectAsState()
    val senderProgress by transferManager.senderProgress.collectAsState()

    // Sender state
    var receiverIpInput by remember {
        mutableStateOf(NetworkUtils.getLocalIpAddress(context))
    }
    var selectionTab by remember { mutableStateOf(SelectionTab.SONGS) }
    var searchQuery by remember { mutableStateOf("") }

    // Selection tracking
    val selectedTrackIds = remember { mutableStateListOf<Long>() }
    // Folder keys: "user_${id}" or "device_${name}"
    val selectedFolderKeys = remember { mutableStateListOf<String>() }
    var expandedFolderKey by remember { mutableStateOf<String?>(null) }

    // Live Transfer Active Dialog & Radar Sheet
    var showSendProgressDialog by remember { mutableStateOf(false) }
    var showDeviceRadarSheet by remember { mutableStateOf(false) }
    var showMaxFolderDialog by remember { mutableStateOf(false) }

    val discoveredDevices by transferManager.discoveredDevices.collectAsState()
    val isSearchingDevices by transferManager.isSearchingDevices.collectAsState()

    // Start/stop receiver server when entering/leaving RECEIVE mode, or start discovery in SEND mode
    DisposableEffect(shareMode) {
        if (shareMode == ShareMode.RECEIVE) {
            transferManager.stopDiscovery()
            transferManager.startReceiver()
        } else {
            transferManager.stopReceiver()
            transferManager.startDiscovery()
        }
        onDispose {
            if (shareMode == ShareMode.RECEIVE) {
                transferManager.stopReceiver()
            } else {
                transferManager.stopDiscovery()
            }
        }
    }

    // Auto open transfer progress dialog when sending begins
    LaunchedEffect(senderProgress) {
        if (senderProgress != null && !showSendProgressDialog) {
            showSendProgressDialog = true
        }
    }

    // Collect all tracks for selected folders (unpacked songs)
    val folderTracksMap = remember(userFolders, deviceFolders) {
        val map = mutableMapOf<String, Pair<String, List<AudioTrack>>>()
        userFolders.forEach { uf ->
            map["user_${uf.id}"] = Pair(uf.name, uf.tracks)
        }
        deviceFolders.forEach { df ->
            map["device_${df.name}"] = Pair(df.name, df.tracks)
        }
        map
    }

    // Calculate total songs to send based on current tab selection
    val tracksToSend: List<AudioTrack> = remember(
        selectionTab,
        selectedTrackIds.toList(),
        selectedFolderKeys.toList(),
        allTracks,
        folderTracksMap
    ) {
        if (selectionTab == SelectionTab.SONGS) {
            allTracks.filter { it.id in selectedTrackIds }
        } else {
            // Unpack songs from the selected folders (max 2)
            val gathered = mutableListOf<AudioTrack>()
            val seenIds = mutableSetOf<Long>()
            selectedFolderKeys.forEach { key ->
                folderTracksMap[key]?.second?.forEach { track ->
                    if (track.id !in seenIds) {
                        seenIds.add(track.id)
                        gathered.add(track)
                    }
                }
            }
            gathered
        }
    }

    val totalBytesToSend = remember(tracksToSend) {
        tracksToSend.sumOf { (it.durationMs * 16L).coerceAtLeast(1024L * 1024L) }
    }

    // Direct helper to send to any target IP
    val startSendingToIp: (String) -> Unit = { targetIp ->
        showDeviceRadarSheet = false
        focusManager.clearFocus()
        scope.launch {
            val items = transferManager.prepareTransferItems(tracksToSend)
            showSendProgressDialog = true
            transferManager.sendItems(
                receiverIp = targetIp.trim(),
                items = items,
                onSuccess = {
                    Toast.makeText(context, "All songs sent successfully!", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Transfer error: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // QR scanner launcher via camera preview
    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val qrText = NetworkUtils.decodeQrFromBitmap(bitmap)
            val ip = qrText?.let { NetworkUtils.parseIpFromPayload(it) }
            if (!ip.isNullOrBlank()) {
                receiverIpInput = ip
                Toast.makeText(context, "Connected to receiver ($ip)!", Toast.LENGTH_SHORT).show()
                startSendingToIp(ip)
            } else {
                Toast.makeText(context, "Could not detect QR code. Try again or tap Hotspot.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Share to",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "High-Speed Wi-Fi Music Sharing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("share_to_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (shareMode == ShareMode.SEND && tracksToSend.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (selectionTab == SelectionTab.SONGS) {
                                        "Songs"
                                    } else {
                                        "Folders"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "~${NetworkUtils.formatFileSize(totalBytesToSend)} total audio data",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    if (tracksToSend.isEmpty()) {
                                        Toast.makeText(context, "Please select at least one song or folder to send", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    showDeviceRadarSheet = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("send_selected_songs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send to Phone",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ShareIt Style Mode Toggle: Send vs Receive
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // Send Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { shareMode = ShareMode.SEND },
                        color = if (shareMode == ShareMode.SEND) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (shareMode == ShareMode.SEND) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send",
                                fontWeight = FontWeight.Bold,
                                color = if (shareMode == ShareMode.SEND) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Receive Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { shareMode = ShareMode.RECEIVE },
                        color = if (shareMode == ShareMode.RECEIVE) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = if (shareMode == ShareMode.RECEIVE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Receive",
                                fontWeight = FontWeight.Bold,
                                color = if (shareMode == ShareMode.RECEIVE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Content
            when (shareMode) {
                ShareMode.RECEIVE -> {
                    ReceiveModeContent(
                        receiverState = receiverState,
                        onCopyIp = { ip ->
                            clipboardManager.setText(AnnotatedString(ip))
                            Toast.makeText(context, "Copied IP: $ip", Toast.LENGTH_SHORT).show()
                        },
                        onOpenLibrary = onOpenLibrary,
                        onRestartReceiver = {
                            transferManager.startReceiver()
                        }
                    )
                }

                ShareMode.SEND -> {
                    SendModeContent(
                        discoveredDevices = discoveredDevices,
                        selectionTab = selectionTab,
                        onSelectionTabChanged = { selectionTab = it },
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        allTracks = allTracks,
                        selectedTrackIds = selectedTrackIds,
                        onToggleTrackSelection = { trackId ->
                            if (trackId in selectedTrackIds) {
                                selectedTrackIds.remove(trackId)
                            } else {
                                selectedTrackIds.add(trackId)
                            }
                        },
                        onSelectAllTracks = {
                            selectedTrackIds.clear()
                            selectedTrackIds.addAll(allTracks.map { it.id })
                        },
                        onClearTrackSelection = {
                            selectedTrackIds.clear()
                        },
                        userFolders = userFolders,
                        deviceFolders = deviceFolders,
                        folderTracksMap = folderTracksMap,
                        selectedFolderKeys = selectedFolderKeys,
                        onToggleFolderSelection = { folderKey ->
                            if (folderKey in selectedFolderKeys) {
                                selectedFolderKeys.remove(folderKey)
                            } else {
                                // Enforce user constraint: maximum 2 folders!
                                if (selectedFolderKeys.size >= 2) {
                                    showMaxFolderDialog = true
                                    Toast.makeText(
                                        context,
                                        "Maximum 2 folders allowed. Please uncheck one folder first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    selectedFolderKeys.add(folderKey)
                                }
                            }
                        },
                        expandedFolderKey = expandedFolderKey,
                        onToggleExpandFolder = { key ->
                            expandedFolderKey = if (expandedFolderKey == key) null else key
                        }
                    )
                }
            }
        }
    }

    // Popup alert message when user attempts to select more than 2 folders
    if (showMaxFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMaxFolderDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Maximum 2 Folders",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You can select a maximum of 2 folders to send at a time. To choose a different folder, please deselect one of your currently selected folders first."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showMaxFolderDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Nearby Device Radar Bottom Sheet
    if (showDeviceRadarSheet) {
        NearbyDeviceRadarSheet(
            discoveredDevices = discoveredDevices,
            isSearching = isSearchingDevices,
            onSendToDevice = { ip ->
                startSendingToIp(ip)
            },
            onScanQr = {
                qrLauncher.launch(null)
            },
            onSendViaHotspot = {
                val gateway = NetworkUtils.getGatewayIpAddress(context) ?: "192.168.43.1"
                Toast.makeText(context, "Connecting to Hotspot ($gateway)...", Toast.LENGTH_SHORT).show()
                startSendingToIp(gateway)
            },
            manualIp = receiverIpInput,
            onManualIpChanged = { receiverIpInput = it },
            onDismiss = { showDeviceRadarSheet = false }
        )
    }

    // Active Live Transfer Dialog (Sending)
    if (showSendProgressDialog && senderProgress != null) {
        val progress = senderProgress!!
        Dialog(
            onDismissRequest = {
                if (progress.isCompleted || progress.errorMessage != null) {
                    showSendProgressDialog = false
                    transferManager.resetSenderState()
                }
            },
            properties = DialogProperties(dismissOnBackPress = progress.isCompleted, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (progress.isCompleted) "Transfer Complete!" else "Sending Audio Data...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (progress.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "⚡ ${NetworkUtils.formatSpeed(progress.speedBytesPerSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Overview
                    if (!progress.isCompleted && progress.errorMessage == null) {
                        // Current song card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Currently Sending:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = progress.currentTitle.ifBlank { "Preparing file..." },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (progress.currentFolder.isNotBlank()) {
                                    Text(
                                        text = "Target Folder: ${progress.currentFolder}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress.currentFileProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${NetworkUtils.formatFileSize(progress.currentFileBytes)} / ${NetworkUtils.formatFileSize(progress.currentFileTotalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(progress.currentFileProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Batch Progress
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Batch Progress (${progress.currentIndex}/${progress.totalCount} songs)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(progress.overallProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.overallProgress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${NetworkUtils.formatFileSize(progress.overallBytesTransferred)} of ${NetworkUtils.formatFileSize(progress.overallBytesTotal)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cancel button
                        OutlinedButton(
                            onClick = {
                                transferManager.cancelSending()
                                showSendProgressDialog = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel Transfer")
                        }
                    } else if (progress.errorMessage != null) {
                        Text(
                            text = "Transfer Error: ${progress.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showSendProgressDialog = false
                                transferManager.resetSenderState()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close")
                        }
                    } else {
                        // Success state
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "All ${progress.totalCount} songs transferred!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Every audio file was streamed and saved on the receiver device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showSendProgressDialog = false
                                    transferManager.resetSenderState()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * UI for Receive Mode with Radar Animation, QR Code, and Incoming File Progress.
 */
@Composable
fun ReceiveModeContent(
    receiverState: ReceiverState,
    onCopyIp: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onRestartReceiver: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_alpha"
    )

    val qrBitmap = remember(receiverState.localIp, receiverState.port) {
        if (receiverState.localIp.isNotBlank()) {
            try {
                NetworkUtils.generateQrCodeBitmap("loopify-share://${receiverState.localIp}:${receiverState.port}", 400)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Radar / Status Hero
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing Radar Circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(110.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(95.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (receiverState.isReceiving) "Receiving Songs..." else "Ready to Receive",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = receiverState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // IP & Port pill with copy button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Receiver IP Address",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${receiverState.localIp}:${receiverState.port}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { onCopyIp(receiverState.localIp) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy IP",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Incoming Transfer Card (if active)
        if (receiverState.isReceiving) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Receiving File (${receiverState.receivedItemsCount + 1}/${receiverState.totalItemsCount})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "⚡ ${NetworkUtils.formatSpeed(receiverState.speedBytesPerSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = receiverState.currentTitle.ifBlank { "Incoming track..." },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (receiverState.currentFolder.isNotBlank()) {
                            Text(
                                text = "Saving into folder: ${receiverState.currentFolder}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { receiverState.currentFileProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        // Overall batch
                        LinearProgressIndicator(
                            progress = { receiverState.overallProgress },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${NetworkUtils.formatFileSize(receiverState.totalBytesReceived)} of ${NetworkUtils.formatFileSize(receiverState.totalBytesExpected)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Transfer Complete Banner
        if (receiverState.isComplete) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transfer Complete!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "${receiverState.receivedFiles.size} songs received and indexed into your library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenLibrary,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Library")
                        }
                    }
                }
            }
        }

        // QR Code Box for Sender Phone
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sender Connection QR",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sender can enter the IP above or scan this code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(170.dp)
                                .padding(4.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Receiver QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hotspot tip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Tip: No Wi-Fi router? Turn on Portable Hotspot on this phone, connect the sender phone, and transfer at top speed anywhere!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * UI for Send Mode with automatic receiver detection, Tabs (Songs vs Folders with 2-folder cap), and preview.
 */
@Composable
fun SendModeContent(
    discoveredDevices: List<DiscoveredDevice>,
    selectionTab: SelectionTab,
    onSelectionTabChanged: (SelectionTab) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    allTracks: List<AudioTrack>,
    selectedTrackIds: List<Long>,
    onToggleTrackSelection: (Long) -> Unit,
    onSelectAllTracks: () -> Unit,
    onClearTrackSelection: () -> Unit,
    userFolders: List<UserFolder>,
    deviceFolders: List<DeviceFolder>,
    folderTracksMap: Map<String, Pair<String, List<AudioTrack>>>,
    selectedFolderKeys: List<String>,
    onToggleFolderSelection: (String) -> Unit,
    expandedFolderKey: String?,
    onToggleExpandFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Clean, elegant Status Banner (No raw IP field clutter)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (discoveredDevices.isNotEmpty())
                Color(0xFF4CAF50).copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (discoveredDevices.isNotEmpty()) Color(0xFF4CAF50).copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (discoveredDevices.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (discoveredDevices.isNotEmpty()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (discoveredDevices.isNotEmpty())
                                "Receiver Detected: ${discoveredDevices.first().name}"
                            else
                                "Wi-Fi Music Sharing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (discoveredDevices.isNotEmpty())
                                "Ready to send • Tap 'Send to Phone' below"
                            else
                                "Select songs or folders, then tap 'Send to Phone'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (discoveredDevices.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selection Tabs: "Songs" vs "Folders"
        TabRow(
            selectedTabIndex = selectionTab.ordinal,
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectionTab.ordinal]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
        ) {
            Tab(
                selected = selectionTab == SelectionTab.SONGS,
                onClick = { onSelectionTabChanged(SelectionTab.SONGS) },
                text = {
                    Text(
                        text = "Songs",
                        fontWeight = if (selectionTab == SelectionTab.SONGS) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectionTab == SelectionTab.SONGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Tab(
                selected = selectionTab == SelectionTab.FOLDERS,
                onClick = { onSelectionTabChanged(SelectionTab.FOLDERS) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Folders",
                            fontWeight = if (selectionTab == SelectionTab.FOLDERS) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectionTab == SelectionTab.FOLDERS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectionTab) {
            SelectionTab.SONGS -> {
                // Search & Quick Select bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("Search songs...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (selectedTrackIds.size == allTracks.size && allTracks.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearTrackSelection,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSelectAllTracks,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("All (${allTracks.size})")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filteredTracks = remember(allTracks, searchQuery) {
                    if (searchQuery.isBlank()) allTracks else {
                        allTracks.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                // Track List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isSelected = track.id in selectedTrackIds
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleTrackSelection(track.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleTrackSelection(track.id) }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.displayTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.displayArtist} • ${track.formattedDuration}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SelectionTab.FOLDERS -> {
                // Folders Mode
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Folder Selection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Every song inside selected folders will be sent as actual audio files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedFolderKeys.size >= 2) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = if (selectedFolderKeys.size >= 2) "Max Folders Selected" else "Selected Folders",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedFolderKeys.size >= 2) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of folders
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Combine user folders and device folders
                    val allFoldersList = mutableListOf<Pair<String, Pair<String, List<AudioTrack>>>>()
                    userFolders.forEach { uf ->
                        allFoldersList.add("user_${uf.id}" to Pair(uf.name, uf.tracks))
                    }
                    deviceFolders.forEach { df ->
                        allFoldersList.add("device_${df.name}" to Pair(df.name, df.tracks))
                    }

                    items(allFoldersList, key = { it.first }) { (folderKey, folderData) ->
                        val (folderName, folderTracks) = folderData
                        val isSelected = folderKey in selectedFolderKeys
                        val isExpanded = expandedFolderKey == folderKey
                        val totalEstimatedSize = folderTracks.sumOf { (it.durationMs * 16L).coerceAtLeast(1024L * 1024L) }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onToggleFolderSelection(folderKey) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleFolderSelection(folderKey) }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (folderKey.startsWith("user_")) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (folderKey.startsWith("user_")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${folderTracks.size} songs • ~${NetworkUtils.formatFileSize(totalEstimatedSize)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Expand / Collapse songs preview button
                                    IconButton(
                                        onClick = { onToggleExpandFolder(folderKey) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Preview Songs",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Expandable Songs List Preview inside this Folder
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Songs in '$folderName' (${folderTracks.size}):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (folderTracks.isEmpty()) {
                                            Text(
                                                text = "No songs in this folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            folderTracks.take(15).forEachIndexed { idx, trk ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}. ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = trk.displayTitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = trk.formattedDuration,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (folderTracks.size > 15) {
                                                Text(
                                                    text = "+ ${folderTracks.size - 15} more songs...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Nearby Devices Radar Bottom Sheet for 1-tap music transfer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyDeviceRadarSheet(
    discoveredDevices: List<DiscoveredDevice>,
    isSearching: Boolean,
    onSendToDevice: (String) -> Unit,
    onScanQr: () -> Unit,
    onSendViaHotspot: () -> Unit,
    manualIp: String,
    onManualIpChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showManualIp by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sheet_radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sheet_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sheet_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Send to Nearby Phone",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fast local Wi-Fi transfer • Zero mobile data used",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Radar Animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Discovered Devices Section
            if (discoveredDevices.isNotEmpty()) {
                Text(
                    text = "Nearby Receivers Found (${discoveredDevices.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    discoveredDevices.forEach { dev ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSendToDevice(dev.ip) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = dev.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ready to receive • Tap to send",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onSendToDevice(dev.ip) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Send", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Searching for receiver on your Wi-Fi...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Make sure the other phone has 'Receive' open in Loopify",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // Instant 1-Tap Connect Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hotspot 1-tap connect
                Button(
                    onClick = onSendViaHotspot,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hotspot Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Scan QR code
                Button(
                    onClick = onScanQr,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Manual IP fallback accordion
            TextButton(
                onClick = { showManualIp = !showManualIp }
            ) {
                Text(
                    text = if (showManualIp) "Hide manual IP" else "Enter IP manually",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (showManualIp) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = onManualIpChanged,
                        label = { Text("Receiver IP") },
                        placeholder = { Text("192.168.43.1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                onSendToDevice(manualIp.trim())
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}
