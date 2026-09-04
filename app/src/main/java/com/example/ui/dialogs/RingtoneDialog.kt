package com.example.ui.dialogs

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioTrack
import com.example.util.RingtoneHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RingtoneDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onRingtoneSetSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val totalDurationSeconds = ((track.durationMs / 1000).toInt()).coerceAtLeast(1)
    val defaultStopSec = minOf(30, totalDurationSeconds)

    var startSec by remember { mutableIntStateOf(0) }
    var stopSec by remember { mutableIntStateOf(defaultStopSec) }
    var selectedTarget by remember { mutableStateOf<RingtoneHelper.RingtoneTarget>(RingtoneHelper.RingtoneTarget.PhoneRingtone) }

    var isProcessing by remember { mutableStateOf(false) }
    var showPermissionPrompt by remember { mutableStateOf(false) }

    // Audio preview state
    var isPreviewPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Monitor preview stop
    LaunchedEffect(isPreviewPlaying) {
        if (isPreviewPlaying) {
            while (isActive && isPreviewPlaying) {
                try {
                    val currentMs = mediaPlayer.currentPosition
                    if (currentMs >= stopSec * 1000) {
                        mediaPlayer.pause()
                        mediaPlayer.seekTo(startSec * 1000)
                        isPreviewPlaying = false
                        break
                    }
                } catch (_: Exception) {
                    isPreviewPlaying = false
                    break
                }
                delay(100)
            }
        }
    }

    fun togglePreview() {
        if (isPreviewPlaying) {
            try {
                mediaPlayer.pause()
            } catch (_: Exception) {}
            isPreviewPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, track.uri)
                mediaPlayer.prepare()
                mediaPlayer.seekTo(startSec * 1000)
                mediaPlayer.start()
                isPreviewPlaying = true
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot preview audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                isPreviewPlaying = false
            }
        }
    }

    fun applyRingtone() {
        if (!RingtoneHelper.canWriteSettings(context)) {
            showPermissionPrompt = true
            return
        }

        // Stop preview if running
        if (isPreviewPlaying) {
            try { mediaPlayer.pause() } catch (_: Exception) {}
            isPreviewPlaying = false
        }

        isProcessing = true
        scope.launch {
            val result = RingtoneHelper.setTrackAsRingtone(
                context = context,
                track = track,
                startSec = startSec,
                stopSec = stopSec,
                target = selectedTarget
            )
            isProcessing = false

            result.onSuccess { msg ->
                Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show()
                onRingtoneSetSuccess(msg)
                onDismiss()
            }.onFailure { err ->
                if (err is SecurityException) {
                    showPermissionPrompt = true
                } else {
                    Toast.makeText(context, "Failed to set ringtone: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_set_ringtone),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set as Ringtone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = track.displayTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(14.dp))

                // Permission Warning Card if needed
                if (showPermissionPrompt) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android requires permission to \"Modify system settings\" to apply ringtones. Tap below to toggle Allow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = {
                                    context.startActivity(RingtoneHelper.getWriteSettingsIntent(context))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Open Settings to Allow", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Range selection (Start sec & Stop sec)
                Text(
                    text = "TRIM DURATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Time info banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            Text("Start Second", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatTime(startSec), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Length", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${stopSec - startSec}s", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Stop Second", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatTime(stopSec), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dual Range Slider
                RangeSlider(
                    value = startSec.toFloat()..stopSec.toFloat(),
                    onValueChange = { range ->
                        val newStart = range.start.toInt().coerceIn(0, totalDurationSeconds - 1)
                        val newStop = range.endInclusive.toInt().coerceIn(newStart + 1, totalDurationSeconds)
                        startSec = newStart
                        stopSec = newStop
                        if (isPreviewPlaying) {
                            try { mediaPlayer.pause() } catch (_: Exception) {}
                            isPreviewPlaying = false
                        }
                    },
                    valueRange = 0f..totalDurationSeconds.toFloat(),
                    steps = if (totalDurationSeconds > 1) totalDurationSeconds - 1 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("ringtone_range_slider")
                )

                // Quick Nudge Buttons (-5s, -1s, +1s, +5s)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start nudge
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NudgeChip("-5s") {
                            startSec = (startSec - 5).coerceAtLeast(0)
                        }
                        NudgeChip("+5s") {
                            startSec = (startSec + 5).coerceAtMost(stopSec - 1)
                        }
                    }

                    // Preview Button
                    FilledTonalButton(
                        onClick = { togglePreview() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("ringtone_preview_btn")
                    ) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPreviewPlaying) "Pause" else "Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Stop nudge
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NudgeChip("-5s") {
                            stopSec = (stopSec - 5).coerceAtLeast(startSec + 1)
                        }
                        NudgeChip("+5s") {
                            stopSec = (stopSec + 5).coerceAtMost(totalDurationSeconds)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sound Target Choice (Phone Ringtone / Notification / Alarm)
                Text(
                    text = "SET AS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetChip(
                        icon = Icons.Default.RingVolume,
                        label = "Ringtone",
                        isSelected = selectedTarget is RingtoneHelper.RingtoneTarget.PhoneRingtone,
                        onClick = { selectedTarget = RingtoneHelper.RingtoneTarget.PhoneRingtone }
                    )
                    TargetChip(
                        icon = Icons.Default.Notifications,
                        label = "Notification",
                        isSelected = selectedTarget is RingtoneHelper.RingtoneTarget.Notification,
                        onClick = { selectedTarget = RingtoneHelper.RingtoneTarget.Notification }
                    )
                    TargetChip(
                        icon = Icons.Default.Alarm,
                        label = "Alarm",
                        isSelected = selectedTarget is RingtoneHelper.RingtoneTarget.Alarm,
                        onClick = { selectedTarget = RingtoneHelper.RingtoneTarget.Alarm }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(14.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { applyRingtone() },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("confirm_set_ringtone_btn")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Setting...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set as ${selectedTarget.label}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TargetChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
