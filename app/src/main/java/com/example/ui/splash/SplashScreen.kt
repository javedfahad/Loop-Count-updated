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
import com.example.ui.components.LoopifyLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening Splash Animation:
 * 1. Logo scales up with a smooth sonic pulse (keeping original custom adaptive icon emblem).
 * 2. Animated Morphing Sequence: Starts displaying "Loopify", then smoothly transforms/reveals "Tuny Music".
 * 3. Elegant "by EERT LABS" badge appears underneath with subtle neon glow and letter spacing.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Logo entrance animation
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }

    // Phase 1: "Loopify" entrance and exit
    val loopifyAlpha = remember { Animatable(0f) }
    val loopifyScale = remember { Animatable(0.9f) }

    // Phase 2: "Tuny Music" entrance
    val tunyAlpha = remember { Animatable(0f) }
    val tunyScale = remember { Animatable(0.85f) }

    // Phase 3: "by EERT LABS" entrance
    val eertLabsAlpha = remember { Animatable(0f) }
    val eertLabsSlide = remember { Animatable(10f) }

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
        // 1. Logo & Initial Brand Entrance
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        }

        // Show "Loopify" first
        launch {
            loopifyAlpha.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            loopifyScale.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
        }

        // Hold "Loopify" momentarily so the transition is clear
        delay(650)

        // Fade out "Loopify"
        launch {
            loopifyAlpha.animateTo(0f, animationSpec = tween(250, easing = FastOutSlowInEasing))
            loopifyScale.animateTo(1.08f, animationSpec = tween(250, easing = FastOutSlowInEasing))
        }

        delay(150)

        // Transform/reveal "Tuny Music"
        launch {
            tunyAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            tunyScale.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }

        // Reveal "by EERT LABS"
        delay(200)
        launch {
            eertLabsAlpha.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            eertLabsSlide.animateTo(0f, animationSpec = tween(350, easing = FastOutSlowInEasing))
        }

        // Display finished state
        delay(850)
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
            // Emblem: The original custom icon design preserved exactly
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
                LoopifyLogo(
                    size = 96.dp,
                    animated = true
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Morphing Title: "Loopify" -> "Tuny Music"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(44.dp)
            ) {
                // Phase 1: "Loopify" fading out
                if (loopifyAlpha.value > 0.01f) {
                    Text(
                        text = "Loopify",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier
                            .scale(loopifyScale.value)
                            .alpha(loopifyAlpha.value)
                    )
                }

                // Phase 2: "Tuny Music" blooming in
                if (tunyAlpha.value > 0.01f) {
                    Text(
                        text = "Tuny Music",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier
                            .scale(tunyScale.value)
                            .alpha(tunyAlpha.value)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phase 3: "small by eert labs" subtitle badge
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "EERT LABS",
                            fontSize = 11.sp,
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
