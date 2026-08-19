package com.example.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Loads artwork from MediaStore or embedded ID3 tags, falling back to a custom vinyl sleeve cover.
 */
@Composable
fun TrackArtwork(
    track: AudioTrack?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    iconSize: Dp = 24.dp,
    showDetailsOnFallback: Boolean = false
) {
    val context = LocalContext.current
    var imageSource by remember(track?.uri) { mutableStateOf<Any?>(track?.albumArtUri) }
    var isImageLoaded by remember(track?.uri) { mutableStateOf(false) }

    // If albumArtUri is null or failed, attempt to extract embedded picture from audio file
    LaunchedEffect(track?.uri) {
        if (track == null) {
            imageSource = null
            return@LaunchedEffect
        }
        if (track.albumArtUri != null) {
            imageSource = track.albumArtUri
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val mmr = MediaMetadataRetriever()
                    if (track.uri.scheme == "content") {
                        mmr.setDataSource(context, track.uri)
                    } else if (track.uri.path != null) {
                        mmr.setDataSource(track.uri.path)
                    }
                    val artBytes = mmr.embeddedPicture
                    mmr.release()
                    if (artBytes != null) {
                        withContext(Dispatchers.Main) {
                            imageSource = artBytes
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to stylized cover
                }
            }
        }
    }

    val gradientColors = remember(track?.title, track?.artist) {
        getTrackGradientColors(track?.title ?: "", track?.artist ?: "")
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (imageSource != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = track?.displayTitle ?: "Album Art",
                contentScale = ContentScale.Crop,
                onSuccess = { isImageLoaded = true },
                onError = {
                    // If MediaStore albumart URI failed, try embedded picture
                    if (imageSource is Uri && track != null) {
                        imageSource = null
                        // Trigger embedded extraction
                        try {
                            val mmr = MediaMetadataRetriever()
                            if (track.uri.scheme == "content") {
                                mmr.setDataSource(context, track.uri)
                            } else if (track.uri.path != null) {
                                mmr.setDataSource(track.uri.path)
                            }
                            val artBytes = mmr.embeddedPicture
                            mmr.release()
                            if (artBytes != null) {
                                imageSource = artBytes
                            }
                        } catch (e: Exception) {
                            imageSource = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Stylized Fallback Artwork Canvas (when no embedded image exists)
        if (imageSource == null || !isImageLoaded) {
            VinylSleeveArtwork(
                track = track,
                gradientColors = gradientColors,
                isPlaying = isPlaying,
                iconSize = iconSize,
                showDetails = showDetailsOnFallback
            )
        }
    }
}

/**
 * Large Album Art Component specifically for the NowPlaying screen.
 */
@Composable
fun NowPlayingArtworkCard(
    track: AudioTrack?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotation"
    )

    Surface(
        modifier = modifier
            .size(240.dp)
            .clip(RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        TrackArtwork(
            track = track,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(32.dp),
            iconSize = 64.dp,
            showDetailsOnFallback = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun VinylSleeveArtwork(
    track: AudioTrack?,
    gradientColors: List<Color>,
    isPlaying: Boolean,
    iconSize: Dp,
    showDetails: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Decorative vinyl groove rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * 0.9f

            // Outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = maxRadius,
                center = centerOffset,
                style = Stroke(width = 1.5f)
            )
            // Mid ring
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = maxRadius * 0.7f,
                center = centerOffset,
                style = Stroke(width = 1.2f)
            )
            // Inner ring
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = maxRadius * 0.45f,
                center = centerOffset,
                style = Stroke(width = 1.0f)
            )
        }

        if (showDetails && track != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = "Track Art",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize * 0.55f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = track.displayTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = track.displayArtist,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                contentDescription = "Track Art",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Deterministically generates a rich 2-color gradient based on string hash
 */
private fun getTrackGradientColors(title: String, artist: String): List<Color> {
    val hash = abs((title + artist).hashCode())
    val palettes = listOf(
        listOf(Color(0xFF6200EE), Color(0xFF03DAC6)),
        listOf(Color(0xFFE91E63), Color(0xFFFF9800)),
        listOf(Color(0xFF3F51B5), Color(0xFF00BCD4)),
        listOf(Color(0xFF009688), Color(0xFF4CAF50)),
        listOf(Color(0xFF673AB7), Color(0xFFE91E63)),
        listOf(Color(0xFF1E88E5), Color(0xFF26A69A)),
        listOf(Color(0xFFFF5722), Color(0xFFFFC107)),
        listOf(Color(0xFF8E24AA), Color(0xFF3949AB)),
        listOf(Color(0xFF0288D1), Color(0xFF26C6DA)),
        listOf(Color(0xFFD81B60), Color(0xFF8E24AA))
    )
    return palettes[hash % palettes.size]
}
