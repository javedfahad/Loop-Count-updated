package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The official Tuny Music Logo Emblem:
 * Exact match to the user's authentic design:
 * - Circular dark disc with concentric depth ring
 * - Left acoustic sound bracket: Pink to Violet gradient (#FF2A6D -> #7928CA)
 * - Right acoustic sound bracket: Violet to Electric Cyan gradient (#7928CA -> #00DFD8)
 * - Center: Crisp right-facing play triangle in bright cyan/teal gradient
 * - Pure, static, zero outer spinning rings.
 */
@Composable
fun LoopifyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = false,
    colorPink: Color = Color(0xFFFF2A6D),
    colorPurple: Color = Color(0xFF7928CA),
    colorCyan: Color = Color(0xFF00DFD8)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // 1. Base Dark Disc
            drawCircle(
                color = Color(0xFF140A26),
                radius = w * 0.49f,
                center = Offset(cx, cy)
            )

            // 2. Subtle Concentric Inner Groove Ring
            drawCircle(
                color = Color(0xFF1E0F38),
                radius = w * 0.36f,
                center = Offset(cx, cy)
            )

            // Inner Central Depth Core
            drawCircle(
                color = Color(0xFF0F051D),
                radius = w * 0.26f,
                center = Offset(cx, cy)
            )

            // 3. Left Acoustic Sound Bracket (Pink to Purple)
            val strokeW = (w * 0.082f).coerceAtLeast(3f)
            val arcRadius = w * 0.38f
            val arcRect = Size(arcRadius * 2f, arcRadius * 2f)
            val arcTopLeft = Offset(cx - arcRadius, cy - arcRadius)

            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(colorPink, colorPurple),
                    startY = cy - arcRadius,
                    endY = cy + arcRadius
                ),
                startAngle = 125f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // 4. Right Acoustic Sound Bracket (Purple to Cyan)
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(colorPurple, colorCyan),
                    startY = cy - arcRadius,
                    endY = cy + arcRadius
                ),
                startAngle = 305f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // 5. Center Play Triangle (Cyan / Turquoise Gradient)
            val playWidth = w * 0.26f
            val playHeight = playWidth * 1.15f
            // Slightly nudge right (+playWidth * 0.06f) for optical center balance
            val leftX = cx - playWidth * 0.40f
            val rightX = cx + playWidth * 0.60f
            val topY = cy - playHeight * 0.50f
            val bottomY = cy + playHeight * 0.50f

            val playPath = Path().apply {
                moveTo(leftX, topY)
                lineTo(rightX, cy)
                lineTo(leftX, bottomY)
                close()
            }

            drawPath(
                path = playPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFA5F3FC), // Soft pale cyan highlight at top-left
                        colorCyan,         // Vibrant electric turquoise in middle
                        Color(0xFF00B4D8)  // Deep cyan at tip
                    ),
                    start = Offset(leftX, topY),
                    end = Offset(rightX, bottomY)
                )
            )
        }
    }
}

/**
 * Official Tuny Music Logo.
 */
@Composable
fun TunyMusicLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = false
) {
    LoopifyLogo(
        modifier = modifier,
        size = size,
        animated = animated
    )
}
