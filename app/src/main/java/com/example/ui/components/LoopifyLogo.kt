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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brand-new modern Loopify Sonic Emblem:
 * Featuring a vibrant neon vinyl groove with orbiting soundwave rings,
 * dual acoustic frequency arches, and a sharp playhead prism.
 */
@Composable
fun LoopifyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = false,
    colorStart: Color = Color(0xFFFF3366),
    colorMid: Color = Color(0xFF7928CA),
    colorEnd: Color = Color(0xFF00DFD8)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loopify_rotation_pulse")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )

    val currentRotation = if (animated) rotationAngle else 0f
    val currentPulse = if (animated) pulseScale else 1f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1F0D3D),
                        Color(0xFF0F0521),
                        Color(0xFF06020D)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.value * 2f, size.value * 2f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.85f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Outer Radiant Sound Ring Gradient
            val ringBrush = Brush.sweepGradient(
                listOf(colorStart, colorMid, colorEnd, colorStart),
                center = Offset(cx, cy)
            )

            // Outer Orbit Track Ring
            drawCircle(
                brush = ringBrush,
                radius = w * 0.42f * currentPulse,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )

            // Dynamic Acoustic Arcs (Left and Right Sonic Grooves)
            drawArc(
                brush = Brush.linearGradient(listOf(colorStart, colorMid)),
                startAngle = 135f + currentRotation,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.30f, cy - h * 0.30f),
                size = Size(w * 0.60f, h * 0.60f),
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            )

            drawArc(
                brush = Brush.linearGradient(listOf(colorMid, colorEnd)),
                startAngle = 315f + currentRotation,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.30f, cy - h * 0.30f),
                size = Size(w * 0.60f, h * 0.60f),
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            )

            // Inner Core Dark Vinyl Disc
            drawCircle(
                color = Color(0xFF160A2C),
                radius = w * 0.22f,
                center = Offset(cx, cy)
            )

            // Glowing Center Playhead Prism
            val playSize = w * 0.22f
            val playPath = Path().apply {
                moveTo(cx - playSize * 0.35f, cy - playSize * 0.45f)
                lineTo(cx + playSize * 0.50f, cy)
                lineTo(cx - playSize * 0.35f, cy + playSize * 0.45f)
                close()
            }

            drawPath(
                path = playPath,
                brush = Brush.linearGradient(
                    listOf(Color.White, colorEnd),
                    start = Offset(cx - playSize * 0.35f, cy - playSize * 0.45f),
                    end = Offset(cx + playSize * 0.50f, cy + playSize * 0.45f)
                )
            )

            // 4 Orbiting Sound Sparks
            drawCircle(color = colorStart, radius = w * 0.035f, center = Offset(cx - w * 0.42f, cy))
            drawCircle(color = colorEnd, radius = w * 0.035f, center = Offset(cx + w * 0.42f, cy))
            drawCircle(color = Color.White, radius = w * 0.025f, center = Offset(cx, cy - h * 0.42f))
            drawCircle(color = colorMid, radius = w * 0.025f, center = Offset(cx, cy + h * 0.42f))
        }
    }
}
