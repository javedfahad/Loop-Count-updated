package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.DeviceFolder
import com.example.model.UserFolder
import com.example.transfer.ShareToPayload
import com.example.transfer.TransferItem
import com.example.transfer.WifiTransferManager
import com.example.ui.components.MultiSelectLogo
import com.example.ui.components.ShareToScannerView
import com.example.ui.components.TrackArtwork
import com.example.util.ShareToQrHelper
import kotlinx.coroutines.launch

enum class ShareToScreenMode {
    OVERVIEW,
    HOST_QR,
    LISTENER_SCAN,
    RECEIVING,
    SELECT_SONG
}

/**
 * Redesigned, clean, minimal Share To screen.
 * Flow: Select song -> Share To -> Create Share -> QR -> Connected.
 * No technical networking jargon (IPs, ports, sockets) exposed to the user.
 * Generates local QR codes using ZXing Core and scans them offline using Google ML Kit + CameraX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToScreen(
    allTracks: List<AudioTrack>,
    userFolders: List<UserFolder>,
    deviceFolders: List<DeviceFolder>,
    transferManager: WifiTransferManager,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    initialSelectedTracks: List<AudioTrack> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Host and receiver states from transferManager
    val hostState by transferManager.hostShareState.collectAsState()

    var screenMode by remember {
        mutableStateOf(
            if (initialSelectedTracks.isNotEmpty()) ShareToScreenMode.HOST_QR else ShareToScreenMode.OVERVIEW
        )
    }

    // Active selected tracks for Host
    val selectedTracks = remember {
        mutableStateListOf<AudioTrack>().apply {
            addAll(initialSelectedTracks)
        }
    }

    // Prepared items for transfer
    var preparedTransferItems by remember { mutableStateOf<List<TransferItem>>(emptyList()) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Listener receiving state
    var isReceivingConnection by remember { mutableStateOf(false) }
    var receivingProgress by remember { mutableFloatStateOf(0f) }
    var receivingStatusMessage by remember { mutableStateOf("Connecting to host...") }
    var receivingErrorMessage by remember { mutableStateOf<String?>(null) }
    var receivedSongNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var isReceiveCompleted by remember { mutableStateOf(false) }

    // Start Host Session and generate QR code
    fun startHostSessionForTracks(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val items = transferManager.prepareTransferItems(tracks)
            preparedTransferItems = items
            val session = transferManager.startHostSharingSession(items)

            val firstTrack = tracks.firstOrNull()
            val payload = ShareToPayload(
                hostIp = session.hostIp,
                port = session.port,
                sessionId = session.sessionId,
                songTitle = firstTrack?.displayTitle ?: "Shared Track",
                artistName = firstTrack?.displayArtist ?: "",
                trackCount = tracks.size
            )

            val bmp = ShareToQrHelper.generateShareToQrBitmap(payload, size = 650)
            qrBitmap = bmp
            screenMode = ShareToScreenMode.HOST_QR
        }
    }

    // Automatically initialize Host if opened with pre-selected songs
    LaunchedEffect(Unit) {
        if (initialSelectedTracks.isNotEmpty()) {
            startHostSessionForTracks(initialSelectedTracks)
        }
    }

    // Clean up host on dispose
    DisposableEffect(Unit) {
        onDispose {
            transferManager.stopHostSharingSession()
        }
    }

    // Handle Back Press
    BackHandler {
        when (screenMode) {
            ShareToScreenMode.OVERVIEW -> onBack()
            ShareToScreenMode.HOST_QR -> {
                transferManager.stopHostSharingSession()
                qrBitmap = null
                if (initialSelectedTracks.isNotEmpty()) {
                    onBack()
                } else {
                    screenMode = ShareToScreenMode.OVERVIEW
                }
            }
            ShareToScreenMode.LISTENER_SCAN -> {
                screenMode = ShareToScreenMode.OVERVIEW
            }
            ShareToScreenMode.RECEIVING -> {
                if (isReceiveCompleted || receivingErrorMessage != null) {
                    screenMode = ShareToScreenMode.OVERVIEW
                } else {
                    screenMode = ShareToScreenMode.OVERVIEW
                }
            }
            ShareToScreenMode.SELECT_SONG -> {
                screenMode = ShareToScreenMode.OVERVIEW
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Share To",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (screenMode) {
                                ShareToScreenMode.HOST_QR -> "Host Mode • Ready to Connect"
                                ShareToScreenMode.LISTENER_SCAN -> "Scan Host's Code"
                                ShareToScreenMode.RECEIVING -> "Connecting & Streaming"
                                ShareToScreenMode.SELECT_SONG -> "Choose Songs to Share"
                                ShareToScreenMode.OVERVIEW -> "Share & Listen Locally"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (screenMode) {
                                ShareToScreenMode.OVERVIEW -> onBack()
                                ShareToScreenMode.HOST_QR -> {
                                    transferManager.stopHostSharingSession()
                                    qrBitmap = null
                                    if (initialSelectedTracks.isNotEmpty()) onBack() else screenMode = ShareToScreenMode.OVERVIEW
                                }
                                ShareToScreenMode.LISTENER_SCAN -> screenMode = ShareToScreenMode.OVERVIEW
                                ShareToScreenMode.RECEIVING -> screenMode = ShareToScreenMode.OVERVIEW
                                ShareToScreenMode.SELECT_SONG -> screenMode = ShareToScreenMode.OVERVIEW
                            }
                        },
                        modifier = Modifier.testTag("share_to_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                },
                actions = {
                    if (screenMode == ShareToScreenMode.HOST_QR) {
                        IconButton(
                            onClick = {
                                transferManager.stopHostSharingSession()
                                qrBitmap = null
                                screenMode = ShareToScreenMode.OVERVIEW
                            },
                            modifier = Modifier.testTag("stop_sharing_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop Sharing",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (screenMode == ShareToScreenMode.OVERVIEW || screenMode == ShareToScreenMode.SELECT_SONG) {
                        // Prominent Select button with MultiSelectLogo as requested
                        Surface(
                            onClick = {
                                if (allTracks.isNotEmpty()) {
                                    screenMode = ShareToScreenMode.SELECT_SONG
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("share_to_top_select_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                MultiSelectLogo(size = 20.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Select",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screenMode) {
                // ==========================================
                // 1. OVERVIEW SCREEN (Clean 2-card entry)
                // ==========================================
                ShareToScreenMode.OVERVIEW -> {
                    ShareToOverviewContent(
                        onStartShare = {
                            if (allTracks.isNotEmpty()) {
                                screenMode = ShareToScreenMode.SELECT_SONG
                            } else {
                                Toast.makeText(context, "No songs in library to share", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onStartScan = {
                            screenMode = ShareToScreenMode.LISTENER_SCAN
                        }
                    )
                }

                // ==========================================
                // 2. HOST QR SCREEN (Large, clean, focused)
                // ==========================================
                ShareToScreenMode.HOST_QR -> {
                    HostQrScreenContent(
                        tracks = selectedTracks,
                        qrBitmap = qrBitmap,
                        listenersCount = hostState.connectedListenersCount,
                        isTransmitting = hostState.isTransmitting,
                        onStopSharing = {
                            transferManager.stopHostSharingSession()
                            qrBitmap = null
                            if (initialSelectedTracks.isNotEmpty()) onBack() else screenMode = ShareToScreenMode.OVERVIEW
                        }
                    )
                }

                // ==========================================
                // 3. SCANNER SCREEN (CameraX + ML Kit)
                // ==========================================
                ShareToScreenMode.LISTENER_SCAN -> {
                    ShareToScannerView(
                        isConnecting = isReceivingConnection,
                        connectingMessage = receivingStatusMessage,
                        onPayloadDetected = { payload ->
                            isReceivingConnection = true
                            receivingStatusMessage = "Connecting to host..."
                            receivingErrorMessage = null
                            isReceiveCompleted = false
                            screenMode = ShareToScreenMode.RECEIVING

                            // Automatically connect to the host IP and port
                            transferManager.connectToHostAndReceive(
                                hostIp = payload.hostIp,
                                hostPort = payload.port,
                                onProgress = { progress, msg ->
                                    receivingProgress = progress
                                    receivingStatusMessage = msg
                                },
                                onComplete = { files ->
                                    isReceivingConnection = false
                                    isReceiveCompleted = true
                                    receivedSongNames = files
                                    receivingStatusMessage = "Connected! Music ready to play."
                                },
                                onError = { error ->
                                    isReceivingConnection = false
                                    receivingErrorMessage = error
                                }
                            )
                        },
                        onClose = {
                            screenMode = ShareToScreenMode.OVERVIEW
                        }
                    )
                }

                // ==========================================
                // 4. RECEIVING SCREEN (Progress & Confirmation)
                // ==========================================
                ShareToScreenMode.RECEIVING -> {
                    ListenerReceivingContent(
                        isCompleted = isReceiveCompleted,
                        progress = receivingProgress,
                        statusMessage = receivingStatusMessage,
                        errorMessage = receivingErrorMessage,
                        receivedSongs = receivedSongNames,
                        onRetry = {
                            screenMode = ShareToScreenMode.LISTENER_SCAN
                        },
                        onDone = {
                            screenMode = ShareToScreenMode.OVERVIEW
                        }
                    )
                }

                // ==========================================
                // 5. SELECT SONG MODAL (When opened standalone)
                // ==========================================
                ShareToScreenMode.SELECT_SONG -> {
                    SongPickerContent(
                        tracks = allTracks,
                        onTracksSelected = { chosenTracks ->
                            selectedTracks.clear()
                            selectedTracks.addAll(chosenTracks)
                            startHostSessionForTracks(chosenTracks)
                        },
                        onCancel = {
                            screenMode = ShareToScreenMode.OVERVIEW
                        }
                    )
                }
            }
        }
    }
}

/**
 * Clean Overview Screen presenting Host or Scan options without technical jargon.
 */
@Composable
private fun ShareToOverviewContent(
    onStartShare: () -> Unit,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero Icon with soft ambient aura
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MultiSelectLogo(
                    size = 44.dp,
                    animated = true
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Share Music Locally",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect with nearby friends using high-speed local offline Wi-Fi. No internet or mobile data needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Action Card 1: Share Songs (Host)
        Card(
            onClick = onStartShare,
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("share_to_card_host")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MultiSelectLogo(
                            size = 32.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Songs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Create a QR code for nearby friends to connect and listen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Card 2: Scan & Connect (Listener)
        Card(
            onClick = onStartScan,
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("share_to_card_listener")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scan to Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Scan the host's QR code with your camera to join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Modern Host QR Screen.
 * Displays artwork, song title & artist, "Ready to share" badge, large centered QR code,
 * and connected listener count.
 */
@Composable
private fun HostQrScreenContent(
    tracks: List<AudioTrack>,
    qrBitmap: Bitmap?,
    listenersCount: Int,
    isTransmitting: Boolean,
    onStopSharing: () -> Unit
) {
    val firstTrack = tracks.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Selected Song Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (firstTrack != null) {
                        TrackArtwork(
                            track = firstTrack,
                            isPlaying = false,
                            shape = RoundedCornerShape(12.dp),
                            iconSize = 22.dp,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (tracks.size > 1) {
                                "${firstTrack?.displayTitle ?: "Selected Song"} (+${tracks.size - 1} more)"
                            } else {
                                firstTrack?.displayTitle ?: "Sharing Song"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = firstTrack?.displayArtist ?: "Local Audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // "Ready to share" badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ready",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Large QR Code Card
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(2.dp, Color.White),
                modifier = Modifier
                    .size(270.dp)
                    .aspectRatio(1f)
                    .testTag("host_qr_code_display")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Share To Connection QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Short User Instruction
            Text(
                text = "Scan this QR code to connect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Open Share To on your friend's device and scan this code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connected Listeners Status Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isTransmitting) Icons.Default.Radio else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (listenersCount > 0) {
                            if (isTransmitting) "$listenersCount listener connected • Sending song..." else "$listenersCount listener connected"
                        } else {
                            "Waiting for listeners to scan..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Bottom Stop Action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Button(
                onClick = onStopSharing,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_stop_sharing")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Sharing", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Clean listener connection feedback screen with progress bar.
 */
@Composable
private fun ListenerReceivingContent(
    isCompleted: Boolean,
    progress: Float,
    statusMessage: String,
    errorMessage: String?,
    receivedSongs: List<String>,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (errorMessage != null) {
            // Error State
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Again", fontWeight = FontWeight.Bold)
            }
        } else if (isCompleted) {
            // Success State
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Connected Successfully!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (receivedSongs.isNotEmpty()) {
                    "Ready to play \"${receivedSongs.first()}\""
                } else {
                    "Audio stream ready to listen"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        } else {
            // Receiving / Connecting Progress State
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Connecting to Host",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

/**
 * Clean, fast multi-select picker to choose tracks to share.
 * Features MultiSelectLogo branding, multi-track selection, and direct sending.
 */
@Composable
private fun SongPickerContent(
    tracks: List<AudioTrack>,
    onTracksSelected: (List<AudioTrack>) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedPickerTracks = remember { mutableStateListOf<AudioTrack>() }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val isAllSelected = filteredTracks.isNotEmpty() && selectedPickerTracks.size == filteredTracks.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs to share...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Header with MultiSelectLogo + Select All / Deselect All Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MultiSelectLogo(size = 22.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedPickerTracks.isEmpty()) "Tap songs to select" else "${selectedPickerTracks.size} of ${filteredTracks.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedPickerTracks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = {
                    if (isAllSelected) {
                        selectedPickerTracks.clear()
                    } else {
                        selectedPickerTracks.clear()
                        selectedPickerTracks.addAll(filteredTracks)
                    }
                },
                modifier = Modifier.testTag("share_picker_select_all_btn")
            ) {
                Text(
                    text = if (isAllSelected) "Deselect All" else "Select All",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredTracks, key = { it.id }) { track ->
                val isSelected = selectedPickerTracks.any { it.uri == track.uri }

                Surface(
                    onClick = {
                        if (isSelected) {
                            selectedPickerTracks.removeAll { it.uri == track.uri }
                        } else {
                            selectedPickerTracks.add(track)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("picker_track_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Selection Indicator
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 2.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        TrackArtwork(
                            track = track,
                            isPlaying = false,
                            shape = RoundedCornerShape(10.dp),
                            iconSize = 18.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.displayArtist} • ${track.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Logo emblem on each item
                        MultiSelectLogo(
                            size = 22.dp,
                            badgeBackground = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons: Send Songs Button + Cancel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (selectedPickerTracks.isNotEmpty()) {
                        onTracksSelected(selectedPickerTracks.toList())
                    }
                },
                enabled = selectedPickerTracks.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_share_selected_tracks")
            ) {
                MultiSelectLogo(
                    size = 20.dp,
                    badgeBackground = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedPickerTracks.size > 1) "Send ${selectedPickerTracks.size} Songs" else if (selectedPickerTracks.size == 1) "Send 1 Song" else "Select Songs to Send",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Cancel")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
