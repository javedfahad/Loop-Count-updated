package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern Loopify Music Logo featuring an Infinity Loop (∞) fused with connected music notes,
 * an electric gradient beam (Cyan -> Purple -> Magenta), centered playhead, and equalizer pulses.
 */
@Composable
fun LoopifyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = false,
    accentCyan: Color = Color(0xFF00F0FF),
    accentPurple: Color = Color(0xFF7000FF),
    accentMagenta: Color = Color(0xFFFF007A),
    accentCenter: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loopify_infinity_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val waveHeightAnim by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_height"
    )

    val actualPulse = if (animated) pulseScale else 1.0f
    val waveScale = if (animated) waveHeightAnim else 1.0f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF23103E),
                        Color(0xFF10061E),
                        Color(0xFF05010B)
                    ),
                    radius = size.value * 2.2f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.82f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Top Connecting Music Beam (Bridge)
            val beamPath = Path().apply {
                moveTo(w * 0.35f, h * 0.30f)
                cubicTo(
                    w * 0.42f, h * 0.26f,
                    w * 0.58f, h * 0.26f,
                    w * 0.65f, h * 0.30f
                )
            }
            val beamBrush = Brush.linearGradient(
                colors = listOf(accentCyan, accentMagenta),
                start = Offset(w * 0.35f, h * 0.30f),
                end = Offset(w * 0.65f, h * 0.30f)
            )
            drawPath(
                path = beamPath,
                brush = beamBrush,
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            )

            // Music Note Stems
            drawLine(
                color = accentCyan,
                start = Offset(w * 0.35f, h * 0.30f),
                end = Offset(w * 0.35f, cy),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentMagenta,
                start = Offset(w * 0.65f, h * 0.30f),
                end = Offset(w * 0.65f, cy),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )

            // Infinity Loop Ribbon (∞)
            val infinityBrush = Brush.linearGradient(
                colors = listOf(accentCyan, accentPurple, accentMagenta),
                start = Offset(w * 0.2f, h * 0.7f),
                end = Offset(w * 0.8f, h * 0.3f)
            )

            val infinityPath = Path().apply {
                moveTo(cx, cy)
                // Left top loop
                cubicTo(
                    cx - w * 0.15f * actualPulse, cy - h * 0.18f * actualPulse,
                    w * 0.22f, cy - h * 0.18f * actualPulse,
                    w * 0.18f, cy
                )
                // Left bottom loop
                cubicTo(
                    w * 0.14f, cy + h * 0.18f * actualPulse,
                    cx - w * 0.15f * actualPulse, cy + h * 0.18f * actualPulse,
                    cx, cy
                )
                // Right top loop
                cubicTo(
                    cx + w * 0.15f * actualPulse, cy - h * 0.18f * actualPulse,
                    w * 0.86f, cy - h * 0.18f * actualPulse,
                    w * 0.82f, cy
                )
                // Right bottom loop
                cubicTo(
                    w * 0.78f, cy + h * 0.18f * actualPulse,
                    cx + w * 0.15f * actualPulse, cy + h * 0.18f * actualPulse,
                    cx, cy
                )
                close()
            }

            drawPath(
                path = infinityPath,
                brush = infinityBrush,
                style = Stroke(
                    width = w * 0.075f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Music Note Solid Discs inside Left & Right Loops
            drawCircle(
                color = accentCyan,
                radius = w * 0.065f,
                center = Offset(w * 0.31f, cy)
            )
            drawCircle(
                color = accentMagenta,
                radius = w * 0.065f,
                center = Offset(w * 0.69f, cy)
            )

            // Center Play Triangle
            val playSize = w * 0.18f
            val playPath = Path().apply {
                moveTo(cx - playSize * 0.35f, cy - playSize * 0.5f)
                lineTo(cx + playSize * 0.55f, cy)
                lineTo(cx - playSize * 0.35f, cy + playSize * 0.5f)
                close()
            }
            drawPath(playPath, accentCenter)

            // Bottom Equalizer Frequency Wave Bars
            val eqY = h * 0.82f
            val barWidth = w * 0.038f

            drawLine(
                color = accentCyan,
                start = Offset(cx - w * 0.16f, eqY - (h * 0.04f * waveScale)),
                end = Offset(cx - w * 0.16f, eqY + (h * 0.04f * waveScale)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentPurple,
                start = Offset(cx - w * 0.08f, eqY - (h * 0.07f * (2f - waveScale))),
                end = Offset(cx - w * 0.08f, eqY + (h * 0.07f * (2f - waveScale))),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(cx, eqY - (h * 0.10f * waveScale)),
                end = Offset(cx, eqY + (h * 0.10f * waveScale)),
                strokeWidth = barWidth * 1.1f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentMagenta,
                start = Offset(cx + w * 0.08f, eqY - (h * 0.07f * waveScale)),
                end = Offset(cx + w * 0.08f, eqY + (h * 0.07f * waveScale)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentMagenta,
                start = Offset(cx + w * 0.16f, eqY - (h * 0.04f * (2f - waveScale))),
                end = Offset(cx + w * 0.16f, eqY + (h * 0.04f * (2f - waveScale))),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
