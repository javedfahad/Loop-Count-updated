package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioTrack
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import com.example.ui.components.NowPlayingArtworkCard
import com.example.ui.dialogs.DualListenBottomSheet
import com.example.ui.dialogs.RepeatCountDialog
import com.example.ui.dialogs.RingtoneDialog
import com.example.sync.BluetoothSyncManager
import com.example.sync.DualSyncConnectionState
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    playerManager: AudioPlayerManager,
    syncManager: BluetoothSyncManager? = null,
    onNavigateToShareTo: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val track = playbackState.currentTrack
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    var showDualListenSheet by remember { mutableStateOf(false) }

    val effectiveSyncManager = syncManager ?: remember {
        com.example.LoopCountApp.instance.bluetoothSyncManager
    }
    val syncState by effectiveSyncManager.uiState.collectAsState()

    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPos by remember { mutableFloatStateOf(0f) }

    var wasPlayingBeforeRingtone by remember { mutableStateOf(false) }

    if (showRepeatDialog) {
        RepeatCountDialog(
            initialCount = playbackState.repeatCountTotal,
            initialStopAfterFinish = playbackState.stopAfterFinish,
            onDismiss = { showRepeatDialog = false },
            onStartRepeat = { count, stopAfterFinish ->
                playerManager.setRepeatCount(count, stopAfterFinish)
            }
        )
    }

    if (showRingtoneDialog && track != null) {
        DisposableEffect(Unit) {
            wasPlayingBeforeRingtone = playbackState.isPlaying
            if (playbackState.isPlaying) {
                playerManager.pause()
            }
            onDispose {
                if (wasPlayingBeforeRingtone) {
                    playerManager.play()
                }
            }
        }

        RingtoneDialog(
            track = track,
            onDismiss = { showRingtoneDialog = false }
        )
    }

    if (showDualListenSheet) {
        DualListenBottomSheet(
            syncManager = effectiveSyncManager,
            onNavigateToShareTo = {
                showDualListenSheet = false
                onNavigateToShareTo?.invoke()
            },
            onDismiss = { showDualListenSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (syncState.connectionState == DualSyncConnectionState.CONNECTED)
                                "SYNCED WITH ${syncState.connectedDeviceName?.uppercase() ?: "FRIEND"}"
                            else
                                "NOW PLAYING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = track?.displayTitle ?: "Loopify Music",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("now_playing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    if (track != null) {
                        IconButton(
                            onClick = { showRingtoneDialog = true },
                            modifier = Modifier.testTag("now_playing_ringtone_action_btn")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_set_ringtone),
                                contentDescription = "Set as Ringtone",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val screenHeight = maxHeight
            val isTabletOrLandscape = maxWidth >= 600.dp
            val currentPos = if (isUserSeeking) userSeekPos.toLong() else playbackState.currentPositionMs
            val duration = playbackState.durationMs.coerceAtLeast(1L)
            val sliderValue = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            if (isTabletOrLandscape) {
                // Dual pane layout for tablets, foldables, and landscape
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Artwork & Track Info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        NowPlayingArtworkCard(
                            track = track,
                            isPlaying = playbackState.isPlaying,
                            modifier = Modifier
                                .size(280.dp)
                                .testTag("now_playing_artwork")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = track?.displayTitle ?: "No Track Selected",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = track?.displayArtist ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Right Pane: Repeat status, Seek bar, Controls, Quick presets
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Bento Repeat / Magic Remix Status Card (Interactive)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    if (playbackState.isMagicRemixActive) {
                                        playerManager.next()
                                    } else {
                                        showRepeatDialog = true
                                    }
                                }
                                .testTag("now_playing_repeat_card"),
                            shape = RoundedCornerShape(18.dp),
                            color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiaryContainer
                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiary
                                                else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surface
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (playbackState.isMagicRemixActive) Icons.Default.AutoAwesome else Icons.Default.Repeat,
                                            contentDescription = if (playbackState.isMagicRemixActive) "Magic Remix" else "Repeat",
                                            tint = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiary
                                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (playbackState.isMagicRemixActive) "MAGIC REMIX ACTIVE" else "REPEAT COUNT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiaryContainer
                                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (playbackState.isMagicRemixActive) "Drop #${playbackState.magicTransitionCount} • Non-stop mashup"
                                            else playbackState.repeatDisplayLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiaryContainer
                                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (playbackState.isMagicRemixActive) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.tertiary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "NEXT: ",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                    text = "${playbackState.magicSliceRemainingSeconds}s",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onTertiary
                                                )
                                            }
                                        }
                                    } else if (playbackState.isRepeatActive) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (playbackState.isInfiniteRepeat) {
                                                    Text(
                                                        text = "LOOP: ",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        text = "∞",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Text(
                                                        text = "LEFT: ",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        text = "${playbackState.remainingCount}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (playbackState.stopAfterFinish && !playbackState.isMagicRemixActive && !playbackState.isInfiniteRepeat) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "⏹ Stop",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            )
                                        }
                                    } else if (!playbackState.isRepeatActive && !playbackState.isMagicRemixActive) {
                                        Text(
                                            text = "Tap to set repeat",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Progress Slider & Timestamps
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = sliderValue,
                                onValueChange = { frac ->
                                    isUserSeeking = true
                                    userSeekPos = frac * duration
                                },
                                onValueChangeFinished = {
                                    isUserSeeking = false
                                    playerManager.seekTo(userSeekPos.toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("now_playing_progress_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = AudioTrack.formatDuration(currentPos),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = AudioTrack.formatDuration(duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Modern Studio Playback Controls Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InteractivePlaybackControls(
                                playbackState = playbackState,
                                playerManager = playerManager,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // Phone vertical layout centered with max width boundary
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val artworkSize = (screenHeight * 0.32f).coerceIn(160.dp, 260.dp)
                    NowPlayingArtworkCard(
                        track = track,
                        isPlaying = playbackState.isPlaying,
                        modifier = Modifier
                            .size(artworkSize)
                            .testTag("now_playing_artwork")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
                    ) {
                        Text(
                            text = track?.displayTitle ?: "No Track Selected",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = track?.displayArtist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                if (playbackState.isMagicRemixActive) {
                                    playerManager.next()
                                } else {
                                    showRepeatDialog = true
                                }
                            }
                            .testTag("now_playing_repeat_card"),
                        shape = RoundedCornerShape(18.dp),
                        color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiaryContainer
                        else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.tertiary
                                            else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isMagicRemixActive) Icons.Default.AutoAwesome else Icons.Default.Repeat,
                                        contentDescription = if (playbackState.isMagicRemixActive) "Magic Remix" else "Repeat",
                                        tint = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiary
                                        else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (playbackState.isMagicRemixActive) "MAGIC REMIX ACTIVE" else "REPEAT COUNT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiaryContainer
                                        else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (playbackState.isMagicRemixActive) "Drop #${playbackState.magicTransitionCount} • Non-stop mashup"
                                        else playbackState.repeatDisplayLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (playbackState.isMagicRemixActive) MaterialTheme.colorScheme.onTertiaryContainer
                                        else if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (playbackState.isMagicRemixActive) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "NEXT: ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = "${playbackState.magicSliceRemainingSeconds}s",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onTertiary
                                            )
                                        }
                                    }
                                } else if (playbackState.isRepeatActive) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (playbackState.isInfiniteRepeat) {
                                                Text(
                                                    text = "LOOP: ",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                    text = "∞",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else {
                                                Text(
                                                    text = "LEFT: ",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                    text = "${playbackState.remainingCount}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (playbackState.stopAfterFinish && !playbackState.isMagicRemixActive && !playbackState.isInfiniteRepeat) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "⏹ Stop",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                } else if (!playbackState.isRepeatActive && !playbackState.isMagicRemixActive) {
                                    Text(
                                        text = "Tap to set repeat",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Live Dual Listen Sync Status Banner if connected
                    if (syncState.connectionState == DualSyncConnectionState.CONNECTED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 480.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showDualListenSheet = true }
                                .testTag("now_playing_sync_status_badge"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothConnected,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dual Listen Active",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Listening together with ${syncState.connectedDeviceName ?: "Friend"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Manage",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { frac ->
                                isUserSeeking = true
                                userSeekPos = frac * duration
                            },
                            onValueChangeFinished = {
                                isUserSeeking = false
                                playerManager.seekTo(userSeekPos.toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("now_playing_progress_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = AudioTrack.formatDuration(currentPos),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = AudioTrack.formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Modern Studio Animated Playback Controls Bar
                    InteractivePlaybackControls(
                        playbackState = playbackState,
                        playerManager = playerManager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun InteractivePlaybackControls(
    playbackState: PlaybackState,
    playerManager: AudioPlayerManager,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var rewindKick by remember { mutableStateOf(false) }
    var forwardKick by remember { mutableStateOf(false) }
    var prevKick by remember { mutableStateOf(false) }
    var nextKick by remember { mutableStateOf(false) }
    var playPressed by remember { mutableStateOf(false) }

    val shuffleRotation by animateFloatAsState(
        targetValue = if (playbackState.isShuffle) 360f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shuffle_rot"
    )
    val shuffleScale by animateFloatAsState(
        targetValue = if (playbackState.isShuffle) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "shuffle_scale"
    )

    val rewindAngle by animateFloatAsState(
        targetValue = if (rewindKick) -35f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "rewind_angle"
    )

    val forwardAngle by animateFloatAsState(
        targetValue = if (forwardKick) 35f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "forward_angle"
    )

    val prevOffset by animateFloatAsState(
        targetValue = if (prevKick) -10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "prev_offset"
    )
    val prevScale by animateFloatAsState(
        targetValue = if (prevKick) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "prev_scale"
    )

    val nextOffset by animateFloatAsState(
        targetValue = if (nextKick) 10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "next_offset"
    )
    val nextScale by animateFloatAsState(
        targetValue = if (nextKick) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "next_scale"
    )

    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "play_scale"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle button with continuous spin & bounce
        IconButton(
            onClick = { playerManager.toggleShuffle() },
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    rotationZ = shuffleRotation
                    scaleX = shuffleScale
                    scaleY = shuffleScale
                }
                .testTag("btn_shuffle")
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playbackState.isShuffle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Rewind 10 seconds button with rotation kick animation
        IconButton(
            onClick = {
                coroutineScope.launch {
                    rewindKick = true
                    delay(160)
                    rewindKick = false
                }
                playerManager.seekBackward10()
            },
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    rotationZ = rewindAngle
                    scaleX = if (rewindKick) 0.88f else 1f
                    scaleY = if (rewindKick) 0.88f else 1f
                }
                .testTag("btn_rewind_10")
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = "Rewind 10 seconds",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
            )
        }

        // Previous track button with horizontal nudge & bounce
        IconButton(
            onClick = {
                coroutineScope.launch {
                    prevKick = true
                    delay(160)
                    prevKick = false
                }
                playerManager.previous()
            },
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    translationX = prevOffset
                    scaleX = prevScale
                    scaleY = prevScale
                }
                .testTag("btn_previous")
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(34.dp)
            )
        }

        // Central Play / Pause button with spring morph & press bounce
        FilledIconButton(
            onClick = {
                coroutineScope.launch {
                    playPressed = true
                    delay(150)
                    playPressed = false
                }
                playerManager.togglePlayPause()
            },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .testTag("btn_play_pause")
        ) {
            AnimatedContent(
                targetState = playbackState.isPlaying,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                label = "play_pause_icon_anim"
            ) { isPlaying ->
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // Next track button with horizontal nudge & bounce
        IconButton(
            onClick = {
                coroutineScope.launch {
                    nextKick = true
                    delay(160)
                    nextKick = false
                }
                playerManager.next()
            },
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    translationX = nextOffset
                    scaleX = nextScale
                    scaleY = nextScale
                }
                .testTag("btn_next")
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(34.dp)
            )
        }

        // Forward 10 seconds button with rotation kick animation
        IconButton(
            onClick = {
                coroutineScope.launch {
                    forwardKick = true
                    delay(160)
                    forwardKick = false
                }
                playerManager.seekForward10()
            },
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    rotationZ = forwardAngle
                    scaleX = if (forwardKick) 0.88f else 1f
                    scaleY = if (forwardKick) 0.88f else 1f
                }
                .testTag("btn_forward_10")
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = "Forward 10 seconds",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}
