package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepBackground = Color(0xFF0F0F1A)
val SurfaceDark = Color(0xFF181829)
val SurfaceVariantDark = Color(0xFF232338)
val ElectricViolet = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF06B6D4)
val SunsetPink = Color(0xFFEC4899)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val AccentGreen = Color(0xFF10B981)

@Composable
fun TunyMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: ThemeAccent = ThemeAccent.PURPLE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val primary = accentColor.primaryColor

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.28f),
            onPrimaryContainer = Color.White,
            secondary = NeonCyan,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF164E63),
            onSecondaryContainer = Color(0xFFA5F3FC),
            tertiary = SunsetPink,
            onTertiary = Color.White,
            background = DeepBackground,
            onBackground = TextPrimary,
            surface = SurfaceDark,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = TextSecondary,
            outline = Color(0xFF374151)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = BentoPurpleContainer,
            onPrimaryContainer = BentoTextPrimaryLight,
            secondary = NeonCyan,
            onSecondary = Color.White,
            background = BentoBackgroundLight,
            onBackground = BentoTextPrimaryLight,
            surface = BentoSurfaceLight,
            onSurface = BentoTextPrimaryLight,
            surfaceVariant = BentoSurfaceVariantLight,
            onSurfaceVariant = BentoTextSecondaryLight,
            outline = BentoBorderLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
