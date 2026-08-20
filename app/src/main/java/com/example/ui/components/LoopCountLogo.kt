package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern LoopCount Logo featuring an electric cyan & vivid purple dual-loop arc,
 * centered play triangle, and audio pulse accents.
 */
@Composable
fun LoopCountLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    accentCyan: Color = Color(0xFF00E5FF),
    accentPurple: Color = Color(0xFFE040FB),
    accentCenter: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF280A48),
                        Color(0xFF130026),
                        Color(0xFF080012)
                    ),
                    radius = size.value * 2f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.76f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            val strokeWidth = w * 0.10f
            val radius = w * 0.36f
            val arcSize = Size(radius * 2f, radius * 2f)
            val arcTopLeft = Offset(cx - radius, cy - radius)

            // Gradient brush for loop ring
            val loopBrush = Brush.linearGradient(
                colors = listOf(accentCyan, Color(0xFF7C4DFF), accentPurple),
                start = Offset(0f, h),
                end = Offset(w, 0f)
            )

            // Clockwise Outer Loop Arc (from bottom-left 140° sweeping 290°)
            val startAngle = 140f
            val sweepAngle = 280f

            drawArc(
                brush = loopBrush,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Top-right Arrowhead
            val endAngleRad = (startAngle + sweepAngle) * (PI.toFloat() / 180f)
            val endX = cx + radius * cos(endAngleRad)
            val endY = cy + radius * sin(endAngleRad)
            val tangentAngle = endAngleRad + (PI.toFloat() / 2f)
            val arrowLength = w * 0.22f
            val arrowWidth = w * 0.20f

            val tipX = endX + arrowLength * 0.45f * cos(tangentAngle)
            val tipY = endY + arrowLength * 0.45f * sin(tangentAngle)
            val normalAngle = tangentAngle + (PI.toFloat() / 2f)
            val baseCenterX = tipX - arrowLength * cos(tangentAngle)
            val baseCenterY = tipY - arrowLength * sin(tangentAngle)

            val arrowPath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(baseCenterX + (arrowWidth / 2f) * cos(normalAngle), baseCenterY + (arrowWidth / 2f) * sin(normalAngle))
                lineTo(baseCenterX + (arrowLength * 0.22f) * cos(tangentAngle), baseCenterY + (arrowLength * 0.22f) * sin(tangentAngle))
                lineTo(baseCenterX - (arrowWidth / 2f) * cos(normalAngle), baseCenterY - (arrowWidth / 2f) * sin(normalAngle))
                close()
            }
            drawPath(arrowPath, accentPurple)

            // Bottom-left Counter Arrow for infinite repeat feel
            val counterStartAngleRad = startAngle * (PI.toFloat() / 180f)
            val counterEndX = cx + radius * cos(counterStartAngleRad)
            val counterEndY = cy + radius * sin(counterStartAngleRad)
            val counterTangent = counterStartAngleRad - (PI.toFloat() / 2f)

            val counterTipX = counterEndX + arrowLength * 0.35f * cos(counterTangent)
            val counterTipY = counterEndY + arrowLength * 0.35f * sin(counterTangent)
            val counterNormal = counterTangent + (PI.toFloat() / 2f)
            val counterBaseX = counterTipX - arrowLength * 0.8f * cos(counterTangent)
            val counterBaseY = counterTipY - arrowLength * 0.8f * sin(counterTangent)

            val counterArrowPath = Path().apply {
                moveTo(counterTipX, counterTipY)
                lineTo(counterBaseX + (arrowWidth * 0.4f) * cos(counterNormal), counterBaseY + (arrowWidth * 0.4f) * sin(counterNormal))
                lineTo(counterBaseX - (arrowWidth * 0.4f) * cos(counterNormal), counterBaseY - (arrowWidth * 0.4f) * sin(counterNormal))
                close()
            }
            drawPath(counterArrowPath, accentCyan)

            // Center Play Triangle
            val playSize = w * 0.22f
            val playPath = Path().apply {
                moveTo(cx - playSize * 0.35f, cy - playSize * 0.5f)
                lineTo(cx + playSize * 0.55f, cy)
                lineTo(cx - playSize * 0.35f, cy + playSize * 0.5f)
                close()
            }
            drawPath(playPath, accentCenter)

            // Equalizer Pulse Accent Bars
            val barW = w * 0.045f
            // Left equalizer bar
            drawRoundRect(
                color = accentCyan.copy(alpha = 0.85f),
                topLeft = Offset(cx - playSize * 0.75f - barW / 2f, cy - h * 0.08f),
                size = Size(barW, h * 0.16f),
                cornerRadius = CornerRadius(barW / 2f)
            )

            // Right equalizer bar
            drawRoundRect(
                color = accentPurple.copy(alpha = 0.85f),
                topLeft = Offset(cx + playSize * 0.85f - barW / 2f, cy - h * 0.09f),
                size = Size(barW, h * 0.18f),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
