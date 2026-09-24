package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.playback.AudioPlayerManager
import com.example.playback.PlaybackState
import kotlinx.coroutines.delay

/**
 * Interactive In-App Dynamic Island Capsule.
 *
 * Sits gracefully at the top of the screen (under status bar / camera notch).
 * Stays visible as long as any audio track is running.
 *
 * - Compact State: Sleek black pill with spinning album art disc, dancing equalizer bars,
 *   and loop count indicator.
 * - Expanded State: Expands on tap to reveal quick playback controls, song details, and full player jump.
 */
@Composable
fun LoopifyDynamicIsland(
    playbackState: PlaybackState,
    playerManager: AudioPlayerManager,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-collapse expanded state after 6 seconds of inactivity
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(6000L)
            isExpanded = false
        }
    }

    // Continuous Vinyl / Disc Rotation when song is running
    val infiniteTransition = rememberInfiniteTransition(label = "island_rot")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "island_vinyl_angle"
    )

    // Animated Live Equalizer Bars
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq_bar_1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(270, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq_bar_2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq_bar_3"
    )

    val capsuleWidth by animateDpAsState(
        targetValue = if (isExpanded) 345.dp else 215.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "island_width"
    )

    val capsuleHeight by animateDpAsState(
        targetValue = if (isExpanded) 88.dp else 42.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "island_height"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 21.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "island_corner"
    )

    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .zIndex(100f)
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(cornerRadius),
            color = Color(0xFF090611),
            shadowElevation = 10.dp,
            modifier = Modifier
                .width(capsuleWidth)
                .height(capsuleHeight)
                .testTag("dynamic_island_capsule")
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(cornerRadius), spotColor = accent.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.7f),
                            Color(0xFF00CEC9).copy(alpha = 0.5f),
                            accent.copy(alpha = 0.7f)
                        )
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isExpanded) {
                        isExpanded = true
                    } else {
                        onOpenNowPlaying()
                    }
                }
        ) {
            if (!isExpanded) {
                // ==========================================
                // COMPACT DYNAMIC ISLAND (Pill HUD)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Spinning Album Art Disc
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B2E))
                            .rotate(if (playbackState.isPlaying) discRotation else 0f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.albumArtUri != null) {
                            AsyncImage(
                                model = track.albumArtUri,
                                contentDescription = "Track Artwork",
                                modifier = Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Center: Equalizer Sound Bars
                    Row(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeBar1 = if (playbackState.isPlaying) bar1 else 0.3f
                        val activeBar2 = if (playbackState.isPlaying) bar2 else 0.3f
                        val activeBar3 = if (playbackState.isPlaying) bar3 else 0.3f

                        EqualizerBar(heightRatio = activeBar1, color = accent)
                        EqualizerBar(heightRatio = activeBar2, color = Color(0xFF00CEC9))
                        EqualizerBar(heightRatio = activeBar3, color = accent)
                    }

                    // Right: Loop / Mode Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            when {
                                playbackState.isMagicRemixActive -> {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Magic Remix",
                                        tint = Color(0xFFFFD166),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "#${playbackState.magicTransitionCount}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD166)
                                    )
                                }
                                playbackState.isInfiniteRepeat -> {
                                    Icon(
                                        imageVector = Icons.Default.AllInclusive,
                                        contentDescription = "Infinite Loop",
                                        tint = accent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                playbackState.isRepeatActive -> {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Loop remaining",
                                        tint = accent,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "${playbackState.remainingCount}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Playing" else "Paused",
                                        tint = if (playbackState.isPlaying) Color(0xFF4ECCA3) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // EXPANDED DYNAMIC ISLAND (Extended Control HUD)
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row: Artwork + Track Info + Collapse Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1B2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.albumArtUri != null) {
                                AsyncImage(
                                    model = track.albumArtUri,
                                    contentDescription = "Track Artwork",
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Song Title and Artist (Tap opens Now Playing)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenNowPlaying() }
                        ) {
                            Text(
                                text = track.displayTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.displayArtist,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Close / Collapse chevron button
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(28.dp).testTag("dynamic_island_collapse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse Dynamic Island",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Bottom Row: Loop Status Pill + Inline Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Mode Badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accent.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onOpenNowPlaying() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val modeLabel = when {
                                    playbackState.isMagicRemixActive -> "Remix #${playbackState.magicTransitionCount}"
                                    playbackState.isInfiniteRepeat -> "Loop ∞"
                                    playbackState.isRepeatActive -> "Loop ${playbackState.remainingCount}x"
                                    else -> "Normal Play"
                                }
                                Text(
                                    text = modeLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (playbackState.isMagicRemixActive) Color(0xFFFFD166) else accent
                                )
                            }
                        }

                        // Mini Controls: Previous, Play/Pause, Next
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { playerManager.previous() },
                                modifier = Modifier.size(30.dp).testTag("dynamic_island_prev_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Song",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .clickable { playerManager.togglePlayPause() }
                                    .testTag("dynamic_island_play_pause_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { playerManager.next() },
                                modifier = Modifier.size(30.dp).testTag("dynamic_island_next_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Song",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerBar(heightRatio: Float, color: Color) {
    val barHeight = (14.dp * heightRatio.coerceIn(0.15f, 1f))
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(1.5.dp))
            .background(color)
    )
}
