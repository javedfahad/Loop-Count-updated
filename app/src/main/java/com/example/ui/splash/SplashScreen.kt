package com.example.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TunyMusicLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening Splash Animation:
 * 1. Shows only "Tuny Music by Eert Labs" along with the correct logo emblem.
 * 2. Clean, elegant entrance with smooth ambient neon pulse.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Logo entrance animation
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }

    // "Tuny Music" entrance
    val titleAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.92f) }

    // "by Eert Labs" subtitle entrance
    val eertLabsAlpha = remember { Animatable(0f) }
    val eertLabsSlide = remember { Animatable(8f) }

    // Subtle continuous ambient pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_splash")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    LaunchedEffect(Unit) {
        // 1. Logo & Emblem Entrance
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }

        delay(200)

        // 2. "Tuny Music" title blooms in smoothly
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
            titleScale.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        }

        delay(180)

        // 3. "by Eert Labs" badge slides and fades in
        launch {
            eertLabsAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            eertLabsSlide.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }

        // Hold display smoothly before proceeding into the app
        delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B0C33),
                        Color(0xFF0C0618),
                        Color(0xFF05020A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblem: The official app logo emblem
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Subtle ambient backlight ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF3366).copy(alpha = 0.25f * glowPulse),
                                    Color(0xFF7928CA).copy(alpha = 0.20f * glowPulse),
                                    Color.Transparent
                                )
                            )
                        )
                )
                TunyMusicLogo(
                    size = 96.dp,
                    animated = true
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title: "Tuny Music"
            Text(
                text = "Tuny Music",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
                modifier = Modifier
                    .scale(titleScale.value)
                    .alpha(titleAlpha.value)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Badge: "by Eert Labs"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alpha(eertLabsAlpha.value)
                    .padding(top = eertLabsSlide.value.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF00DFD8).copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "by ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Eert Labs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00DFD8),
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }
    }
}
