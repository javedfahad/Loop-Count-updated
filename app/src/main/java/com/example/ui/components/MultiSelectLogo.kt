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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern, vibrant Multi-Select emblem designed specifically for Tuny Music.
 * Features stacked song tracks with glowing checkmarks, rounded geometric accents,
 * and high-contrast Material 3 palette for instant visual recognition.
 */
@Composable
fun MultiSelectLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    animated: Boolean = false,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    ),
    badgeBackground: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val cornerRadius = size * 0.28f

    val infiniteTransition = rememberInfiniteTransition(label = "multi_select_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val actualModifier = if (animated) {
        modifier.size(size * pulseScale)
    } else {
        modifier.size(size)
    }

    Box(
        modifier = actualModifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(badgeBackground),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height

            val brush = Brush.linearGradient(
                colors = gradientColors,
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )

            val strokeWidth = (w * 0.12f).coerceAtLeast(1.8f)

            // 1. First Row Checkmark + Line
            drawCheckmark(
                startX = w * 0.05f,
                startY = h * 0.22f,
                scale = w * 0.26f,
                brush = brush,
                strokeWidth = strokeWidth
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(w * 0.42f, h * 0.18f),
                size = Size(w * 0.52f, strokeWidth * 1.1f),
                cornerRadius = CornerRadius(strokeWidth, strokeWidth)
            )

            // 2. Second Row Checkmark + Line
            drawCheckmark(
                startX = w * 0.05f,
                startY = h * 0.50f,
                scale = w * 0.26f,
                brush = brush,
                strokeWidth = strokeWidth
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(w * 0.42f, h * 0.46f),
                size = Size(w * 0.42f, strokeWidth * 1.1f),
                cornerRadius = CornerRadius(strokeWidth, strokeWidth)
            )

            // 3. Third Row Checkmark + Line
            drawCheckmark(
                startX = w * 0.05f,
                startY = h * 0.78f,
                scale = w * 0.26f,
                brush = brush,
                strokeWidth = strokeWidth
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(w * 0.42f, h * 0.74f),
                size = Size(w * 0.48f, strokeWidth * 1.1f),
                cornerRadius = CornerRadius(strokeWidth, strokeWidth)
            )
        }
    }
}

private fun DrawScope.drawCheckmark(
    startX: Float,
    startY: Float,
    scale: Float,
    brush: Brush,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(startX, startY + scale * 0.35f)
        lineTo(startX + scale * 0.38f, startY + scale * 0.72f)
        lineTo(startX + scale * 0.95f, startY + scale * 0.05f)
    }

    drawPath(
        path = path,
        brush = brush,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
