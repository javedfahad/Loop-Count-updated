package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import com.example.ui.dialogs.RepeatCountDialog
import com.example.ui.dialogs.RingtoneDialog
import com.example.ui.theme.SunsetPink

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    playerManager: AudioPlayerManager,
    onOpenDualListen: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val track = playbackState.currentTrack
    val isPlaying = playbackState.isPlaying
    val currentPos = playbackState.currentPositionMs
    val totalDuration = if (playbackState.durationMs > 0) playbackState.durationMs else (track?.durationMs ?: 0L)

    var showRepeatDialog by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }

    // User sliding progress state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }

    val sliderProgress = if (isDraggingSlider) {
        sliderDragValue
    } else {
        if (totalDuration > 0) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val displayPosMs = if (isDraggingSlider) {
        (sliderDragValue * totalDuration).toLong()
    } else {
        currentPos
    }

    if (showRepeatDialog) {
        RepeatCountDialog(
            initialCount = playbackState.repeatCountTotal,
            initialStopAfterFinish = playbackState.stopAfterFinish,
            onDismiss = { showRepeatDialog = false },
            onStartRepeat = { count, stopAfter ->
                playerManager.setRepeatCount(count, stopAfter)
                showRepeatDialog = false
            }
        )
    }

    if (showRingtoneDialog && track != null) {
        RingtoneDialog(
            track = track,
            onDismiss = { showRingtoneDialog = false },
            onRingtoneSetSuccess = {
                showRingtoneDialog = false
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_screen")
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isTwoPane = screenWidth >= 600.dp || (screenWidth > screenHeight && screenWidth > 500.dp)

        // Dynamic scale factor based on screen height and width:
        // Ensures the UI scales gracefully down on compact/flip screens and up on tall phones/tablets
        val heightScale = (screenHeight / 760.dp).coerceIn(0.72f, 1.15f)
        val widthScale = (screenWidth / 390.dp).coerceIn(0.80f, 1.25f)
        val scale = kotlin.math.min(heightScale, widthScale)

        // Dynamic album art size that expands to look prominent and bold on every screen:
        // On normal phone: ~220-270dp; scales down cleanly on flip screens without overflowing.
        val targetArtWidth = screenWidth * 0.72f
        val targetArtHeight = screenHeight * 0.38f
        val computedArtSize: Dp = min(targetArtWidth, targetArtHeight)
        val artSize = computedArtSize.coerceIn(150.dp, 300.dp)

        val horizontalPadding = (20.dp * widthScale).coerceIn(16.dp, 28.dp)

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top bar: chevron-down (left), "NOW PLAYING" in small letter-spaced caps, song title below it (one line)
            TopBar(
                songTitle = track?.displayTitle ?: "No Track Playing",
                onBack = onBack,
                scale = scale,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 4.dp * scale)
            )

            if (isTwoPane) {
                // Two-Pane Responsive Layout for Tablets & Landscape
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 6.dp * scale),
                    horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Art, Title, Artist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AlbumArtCard(
                            track = track,
                            isPlaying = isPlaying,
                            size = min(artSize * 1.2f, screenHeight * 0.7f),
                            scale = scale
                        )

                        Spacer(modifier = Modifier.height(14.dp * scale))

                        // Bold song title, artist below
                        SongTitleArtistBlock(
                            title = track?.displayTitle ?: "No Track Playing",
                            artist = track?.displayArtist ?: "Tuny Music",
                            scale = scale
                        )

                        // Magic Remix indicator if active
                        if (playbackState.isMagicRemixActive) {
                            Spacer(modifier = Modifier.height(8.dp * scale))
                            MagicRemixBadge(
                                remainingSeconds = playbackState.magicSliceRemainingSeconds,
                                onStop = { playerManager.stopMagicRemix() },
                                scale = scale
                            )
                        }
                    }

                    // Right Pane: Pills, Repeat, Slider, Controls
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 4. One rounded pill with two buttons: Ringtone | Shuffle
                        RingtoneShufflePill(
                            isShuffle = playbackState.isShuffle,
                            onToggleShuffle = {
                                playerManager.toggleShuffle()
                                val isNowShuffle = !playbackState.isShuffle
                                Toast.makeText(
                                    context,
                                    if (isNowShuffle) "Shuffle ON" else "Shuffle OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onOpenRingtone = {
                                if (track != null) {
                                    showRingtoneDialog = true
                                } else {
                                    Toast.makeText(context, "No track selected", Toast.LENGTH_SHORT).show()
                                }
                            },
                            scale = scale,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp * scale))

                        // 5. Small pill with text-only options: 2x 3x 4x
                        SpeedPill(
                            currentSpeed = playbackState.playbackSpeed,
                            onSelectSpeed = { speed ->
                                playerManager.setPlaybackSpeed(speed)
                            },
                            scale = scale,
                            modifier = Modifier.widthIn(max = 300.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp * scale))

                        // 6. Repeat card: repeat icon in circle, "REPEAT COUNT", "Off" below, "Tap to set repeat" right
                        RepeatCard(
                            playbackState = playbackState,
                            onClick = { showRepeatDialog = true },
                            scale = scale,
                            modifier = Modifier.widthIn(max = 440.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp * scale))

                        // 7. Custom Seekbar with thin track, thin vertical thumb with small dot, times below
                        CustomSeekbar(
                            progress = sliderProgress,
                            currentPosMs = displayPosMs,
                            totalDurationMs = totalDuration,
                            onSeekProgress = {
                                isDraggingSlider = true
                                sliderDragValue = it
                            },
                            onSeekFinished = {
                                val targetMs = (sliderDragValue * totalDuration).toLong()
                                playerManager.seekTo(targetMs)
                                isDraggingSlider = false
                            },
                            scale = scale,
                            modifier = Modifier.widthIn(max = 440.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp * scale))

                        // 8. Controls row in curved rectangle design with subtle press animation
                        CurvedControlsRow(
                            isPlaying = isPlaying,
                            onReplay10 = { playerManager.seekBackward10() },
                            onPrevious = { playerManager.previous() },
                            onTogglePlayPause = { playerManager.togglePlayPause() },
                            onNext = { playerManager.next() },
                            onForward10 = { playerManager.seekForward10() },
                            scale = scale,
                            modifier = Modifier.widthIn(max = 440.dp)
                        )
                    }
                }
            } else {
                // Single-screen portrait layout: natural cohesive spacing without big gaps
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp * scale)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // 2. Album art card: big rounded square, diagonal gradient, faint circles, music-note icon in circle, title and artist inside
                    AlbumArtCard(
                        track = track,
                        isPlaying = isPlaying,
                        size = artSize,
                        scale = scale
                    )

                    Spacer(modifier = Modifier.height(2.dp * scale))

                    // 3. Big bold song title, artist below it
                    SongTitleArtistBlock(
                        title = track?.displayTitle ?: "No Track Playing",
                        artist = track?.displayArtist ?: "Tuny Music",
                        scale = scale
                    )

                    // Magic Remix indicator if active
                    if (playbackState.isMagicRemixActive) {
                        MagicRemixBadge(
                            remainingSeconds = playbackState.magicSliceRemainingSeconds,
                            onStop = { playerManager.stopMagicRemix() },
                            scale = scale
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp * scale))

                    // 4. One rounded pill with two buttons: Ringtone | Shuffle, with a thin divider
                    RingtoneShufflePill(
                        isShuffle = playbackState.isShuffle,
                        onToggleShuffle = {
                            playerManager.toggleShuffle()
                            val isNowShuffle = !playbackState.isShuffle
                            Toast.makeText(
                                context,
                                if (isNowShuffle) "Shuffle ON" else "Shuffle OFF",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenRingtone = {
                            if (track != null) {
                                showRingtoneDialog = true
                            } else {
                                Toast.makeText(context, "No track selected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        scale = scale,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
                    )

                    // 5. Small pill with text-only options: 2x 3x 4x. Selected one highlighted
                    SpeedPill(
                        currentSpeed = playbackState.playbackSpeed,
                        onSelectSpeed = { speed ->
                            playerManager.setPlaybackSpeed(speed)
                        },
                        scale = scale,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )

                    // 6. Repeat card: repeat icon in a circle, "REPEAT COUNT" label, "Off" below, "Tap to set repeat" on the right. Whole card is tappable.
                    RepeatCard(
                        playbackState = playbackState,
                        onClick = { showRepeatDialog = true },
                        scale = scale,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
                    )

                    // 7. Seekbar: thin track, thin vertical bar as thumb, small dot at the end. Times below.
                    CustomSeekbar(
                        progress = sliderProgress,
                        currentPosMs = displayPosMs,
                        totalDurationMs = totalDuration,
                        onSeekProgress = {
                            isDraggingSlider = true
                            sliderDragValue = it
                        },
                        onSeekFinished = {
                            val targetMs = (sliderDragValue * totalDuration).toLong()
                            playerManager.seekTo(targetMs)
                            isDraggingSlider = false
                        },
                        scale = scale,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
                    )

                    // 8. Controls row: Replay-10, Previous, big Play/Pause with soft glow, Next, Forward-10 in curved rectangle design
                    CurvedControlsRow(
                        isPlaying = isPlaying,
                        onReplay10 = { playerManager.seekBackward10() },
                        onPrevious = { playerManager.previous() },
                        onTogglePlayPause = { playerManager.togglePlayPause() },
                        onNext = { playerManager.next() },
                        onForward10 = { playerManager.seekForward10() },
                        scale = scale,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp * scale))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 1. Top Bar
// -----------------------------------------------------------------------------
@Composable
private fun TopBar(
    songTitle: String,
    onBack: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size((42.dp * scale).coerceIn(36.dp, 48.dp))
                .testTag("now_playing_back")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Dismiss Now Playing",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size((28.dp * scale).coerceIn(24.dp, 32.dp))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NOW PLAYING",
                fontSize = ((11f * scale).coerceIn(10f, 13f)).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = songTitle,
                fontSize = ((12f * scale).coerceIn(11f, 14f)).sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(
            modifier = Modifier.size((42.dp * scale).coerceIn(36.dp, 48.dp))
        )
    }
}

// -----------------------------------------------------------------------------
// 2. Album Art Card
// -----------------------------------------------------------------------------
@Composable
private fun AlbumArtCard(
    track: AudioTrack?,
    isPlaying: Boolean,
    size: Dp,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val cornerRadius = (28.dp * scale).coerceIn(20.dp, 36.dp)

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                )
            )
            .drawBehind {
                val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val maxRadius = (size.toPx() / 2f) * 0.92f

                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = maxRadius,
                    center = centerOffset,
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = maxRadius * 0.68f,
                    center = centerOffset,
                    style = Stroke(width = 1.2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = maxRadius * 0.42f,
                    center = centerOffset,
                    style = Stroke(width = 1.0f)
                )
            }
            .testTag("now_playing_artwork"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding((16.dp * scale).coerceIn(12.dp, 24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val circleSize = (56.dp * scale).coerceIn(44.dp, 70.dp)
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music Art",
                    tint = Color.White,
                    modifier = Modifier.size(circleSize * 0.55f)
                )
            }

            Spacer(modifier = Modifier.height((10.dp * scale).coerceIn(6.dp, 14.dp)))

            Text(
                text = track?.displayTitle ?: "Tuny Music",
                fontSize = ((14f * scale).coerceIn(12f, 16f)).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track?.displayArtist ?: "Offline Library",
                fontSize = ((11.5f * scale).coerceIn(10f, 13f)).sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 3. Song Title & Artist Block
// -----------------------------------------------------------------------------
@Composable
private fun SongTitleArtistBlock(
    title: String,
    artist: String,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = ((20f * scale).coerceIn(17f, 24f)).sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_playing_title")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = ((13.5f * scale).coerceIn(11.5f, 15f)).sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_playing_artist")
        )
    }
}

// -----------------------------------------------------------------------------
// 4. One Rounded Pill with Two Buttons: Ringtone | Shuffle
// -----------------------------------------------------------------------------
@Composable
private fun RingtoneShufflePill(
    isShuffle: Boolean,
    onToggleShuffle: () -> Unit,
    onOpenRingtone: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val pillHeight = (40.dp * scale).coerceIn(36.dp, 46.dp)
    val corner = (20.dp * scale).coerceIn(18.dp, 23.dp)

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier.height(pillHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PressableRow(
                onClick = onOpenRingtone,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("now_playing_ringtone_capsule")
            ) {
                Icon(
                    imageVector = Icons.Default.RingVolume,
                    contentDescription = "Set Ringtone",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((16.dp * scale).coerceIn(14.dp, 18.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ringtone",
                    fontSize = ((12.5f * scale).coerceIn(11f, 14f)).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            PressableRow(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isShuffle) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .testTag("now_playing_shuffle_capsule")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((16.dp * scale).coerceIn(14.dp, 18.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isShuffle) "Shuffle ON" else "Shuffle",
                    fontSize = ((12.5f * scale).coerceIn(11f, 14f)).sp,
                    fontWeight = if (isShuffle) FontWeight.Bold else FontWeight.Medium,
                    color = if (isShuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. Small Pill with Text-Only Options: 2x 3x 4x
// -----------------------------------------------------------------------------
@Composable
private fun SpeedPill(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(2.0f, 3.0f, 4.0f)
    val pillHeight = (34.dp * scale).coerceIn(30.dp, 38.dp)
    val corner = (17.dp * scale).coerceIn(15.dp, 19.dp)

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = modifier.height(pillHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            speedOptions.forEach { speed ->
                val isSelected = (currentSpeed == speed)
                val label = "${speed.toInt()}x"

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = spring(),
                    label = "speed_press"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 2.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(RoundedCornerShape(corner))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onSelectSpeed(speed)
                        }
                        .testTag("speed_chip_$label"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = ((12.5f * scale).coerceIn(11f, 14f)).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 6. Repeat Card
// -----------------------------------------------------------------------------
@Composable
private fun RepeatCard(
    playbackState: PlaybackState,
    onClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val corner = (18.dp * scale).coerceIn(14.dp, 22.dp)
    val circleSize = (38.dp * scale).coerceIn(32.dp, 42.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(),
        label = "repeat_card_press"
    )

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag("now_playing_repeat_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = (14.dp * scale).coerceIn(10.dp, 18.dp),
                    vertical = (9.dp * scale).coerceIn(7.dp, 12.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(
                        if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playbackState.repeatCountTotal == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(circleSize * 0.55f)
                )
            }

            Spacer(modifier = Modifier.width((12.dp * scale).coerceIn(8.dp, 14.dp)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REPEAT COUNT",
                    fontSize = ((10.5f * scale).coerceIn(9.5f, 12f)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        !playbackState.isRepeatActive -> "Off"
                        playbackState.isInfiniteRepeat -> "Infinite (∞)"
                        playbackState.repeatCountTotal == 1 -> "1 Time (Remaining: ${playbackState.remainingCount})"
                        else -> "${playbackState.repeatCountTotal} Times (Remaining: ${playbackState.remainingCount})"
                    },
                    fontSize = ((12.5f * scale).coerceIn(11.5f, 14.5f)).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (playbackState.isRepeatActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = if (playbackState.isRepeatActive) "Change repeat" else "Tap to set repeat",
                fontSize = ((11.5f * scale).coerceIn(10.5f, 13f)).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 7. Custom Seekbar
// -----------------------------------------------------------------------------
@Composable
private fun CustomSeekbar(
    progress: Float,
    currentPosMs: Long,
    totalDurationMs: Long,
    onSeekProgress: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.testTag("now_playing_slider")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((28.dp * scale).coerceIn(24.dp, 32.dp))
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                        onSeekFinished()
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeekProgress(newProgress)
                        },
                        onDragEnd = {
                            onSeekFinished()
                        },
                        onDragCancel = {
                            onSeekFinished()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((18.dp * scale).coerceIn(16.dp, 22.dp))
            ) {
                val trackHeight = 3.5.dp.toPx()
                val centerY = size.height / 2f
                val clampedProgress = progress.coerceIn(0f, 1f)
                val thumbX = clampedProgress * size.width

                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                if (thumbX > 0f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(thumbX, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                }

                val thumbWidth = 2.5.dp.toPx()
                val thumbHeight = 15.dp.toPx()
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
                )

                val dotRadius = 4.dp.toPx()
                drawCircle(
                    color = thumbColor,
                    radius = dotRadius,
                    center = Offset(thumbX, centerY + thumbHeight / 2f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = AudioTrack.formatDuration(currentPosMs),
                fontSize = ((11.5f * scale).coerceIn(10.5f, 13.5f)).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            Text(
                text = AudioTrack.formatDuration(totalDurationMs),
                fontSize = ((11.5f * scale).coerceIn(10.5f, 13.5f)).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 8. Controls Row
// -----------------------------------------------------------------------------
@Composable
private fun CurvedControlsRow(
    isPlaying: Boolean,
    onReplay10: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onForward10: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val corner = (22.dp * scale).coerceIn(18.dp, 26.dp)

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = modifier.testTag("now_playing_curved_controls")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = (10.dp * scale).coerceIn(8.dp, 16.dp),
                    vertical = (7.dp * scale).coerceIn(5.dp, 10.dp)
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIconButton(
                onClick = onReplay10,
                icon = Icons.Default.FastRewind,
                contentDescription = "Rewind 10 seconds",
                iconSize = (23.dp * scale).coerceIn(21.dp, 27.dp),
                buttonSize = (42.dp * scale).coerceIn(38.dp, 46.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "now_playing_rewind10"
            )

            ControlIconButton(
                onClick = onPrevious,
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                iconSize = (29.dp * scale).coerceIn(25.dp, 33.dp),
                buttonSize = (44.dp * scale).coerceIn(40.dp, 48.dp),
                tint = MaterialTheme.colorScheme.onSurface,
                testTag = "now_playing_prev"
            )

            val playButtonSize = (58.dp * scale).coerceIn(52.dp, 66.dp)
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
            val playPressScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.92f else 1f,
                animationSpec = spring(),
                label = "play_press"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(playButtonSize + 8.dp)
                    .graphicsLayer {
                        scaleX = playPressScale
                        scaleY = playPressScale
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(playButtonSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(playButtonSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null
                        ) { onTogglePlayPause() }
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(playButtonSize * 0.58f)
                    )
                }
            }

            ControlIconButton(
                onClick = onNext,
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                iconSize = (29.dp * scale).coerceIn(25.dp, 33.dp),
                buttonSize = (44.dp * scale).coerceIn(40.dp, 48.dp),
                tint = MaterialTheme.colorScheme.onSurface,
                testTag = "now_playing_next"
            )

            ControlIconButton(
                onClick = onForward10,
                icon = Icons.Default.FastForward,
                contentDescription = "Forward 10 seconds",
                iconSize = (23.dp * scale).coerceIn(21.dp, 27.dp),
                buttonSize = (42.dp * scale).coerceIn(38.dp, 46.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "now_playing_forward10"
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Helper UI Components
// -----------------------------------------------------------------------------
@Composable
private fun ControlIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconSize: Dp,
    buttonSize: Dp,
    tint: Color,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(),
        label = "ctrl_press"
    )

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun PressableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(),
        label = "pill_press"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
private fun MagicRemixBadge(
    remainingSeconds: Int,
    onStop: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = SunsetPink.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, SunsetPink.copy(alpha = 0.4f)),
        modifier = modifier.clickable { onStop() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp * scale, vertical = 4.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = SunsetPink,
                modifier = Modifier.size(13.dp * scale)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "✨ Magic Remix (${remainingSeconds}s) • Stop",
                fontSize = ((11f * scale).coerceIn(10f, 12f)).sp,
                fontWeight = FontWeight.SemiBold,
                color = SunsetPink
            )
        }
    }
}
