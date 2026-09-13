package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getBentoColorScheme(isDark: Boolean, accent: ThemeAccent) = when (accent) {
    ThemeAccent.PURPLE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFA78BFA),
            onPrimary = Color(0xFF1E1035),
            primaryContainer = Color(0xFF2E1C59),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = Color(0xFFC4B5FD),
            onSecondary = Color(0xFF2E244D),
            secondaryContainer = Color(0xFF3B2D64),
            onSecondaryContainer = Color(0xFFDDD6FE),
            tertiary = Color(0xFFFF3366),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7C3AED),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoPurpleContainer,
            onPrimaryContainer = Color(0xFF2E1065),
            secondary = Color(0xFF6D28D9),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFEDE9FE),
            onSecondaryContainer = Color(0xFF1E1B4B),
            tertiary = Color(0xFFE11D48),
            background = BentoBackgroundLight,
            surface = BentoSurfaceLight,
            surfaceVariant = BentoSurfaceVariantLight,
            onBackground = BentoTextPrimaryLight,
            onSurface = BentoTextPrimaryLight,
            outline = BentoBorderLight
        )
    }
    ThemeAccent.BLUE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF032840),
            primaryContainer = Color(0xFF0E3A5D),
            onPrimaryContainer = Color(0xFFE0F2FE),
            secondary = Color(0xFF7DD3FC),
            onSecondary = Color(0xFF082F49),
            secondaryContainer = Color(0xFF164E63),
            onSecondaryContainer = Color(0xFFBAE6FD),
            tertiary = Color(0xFF00DFD8),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0284C7),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoBlueContainer,
            onPrimaryContainer = Color(0xFF082F49),
            secondary = Color(0xFF0369A1),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0F2FE),
            onSecondaryContainer = Color(0xFF0C4A6E),
            tertiary = Color(0xFF0D9488),
            background = BentoBackgroundLight,
            surface = BentoSurfaceLight,
            surfaceVariant = BentoSurfaceVariantLight,
            onBackground = BentoTextPrimaryLight,
            onSurface = BentoTextPrimaryLight,
            outline = BentoBorderLight
        )
    }
    ThemeAccent.GREEN -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF34D399),
            onPrimary = Color(0xFF022C22),
            primaryContainer = Color(0xFF064E3B),
            onPrimaryContainer = Color(0xFFD1FAE5),
            secondary = Color(0xFF6EE7B7),
            onSecondary = Color(0xFF064E3B),
            secondaryContainer = Color(0xFF047857),
            onSecondaryContainer = Color(0xFFA7F3D0),
            tertiary = Color(0xFF10B981),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF059669),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoGreenContainer,
            onPrimaryContainer = Color(0xFF022C22),
            secondary = Color(0xFF047857),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD1FAE5),
            onSecondaryContainer = Color(0xFF064E3B),
            tertiary = Color(0xFF0F766E),
            background = BentoBackgroundLight,
            surface = BentoSurfaceLight,
            surfaceVariant = BentoSurfaceVariantLight,
            onBackground = BentoTextPrimaryLight,
            onSurface = BentoTextPrimaryLight,
            outline = BentoBorderLight
        )
    }
    ThemeAccent.ROSE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFFB7185),
            onPrimary = Color(0xFF3F0713),
            primaryContainer = Color(0xFF5C1425),
            onPrimaryContainer = Color(0xFFFFE4E6),
            secondary = Color(0xFFFDA4AF),
            onSecondary = Color(0xFF4C0519),
            secondaryContainer = Color(0xFF881337),
            onSecondaryContainer = Color(0xFFFECDD3),
            tertiary = Color(0xFFFF3366),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFE11D48),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoRoseContainer,
            onPrimaryContainer = Color(0xFF4C0519),
            secondary = Color(0xFFBE123C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFE4E6),
            onSecondaryContainer = Color(0xFF881337),
            tertiary = Color(0xFF9F1239),
            background = BentoBackgroundLight,
            surface = BentoSurfaceLight,
            surfaceVariant = BentoSurfaceVariantLight,
            onBackground = BentoTextPrimaryLight,
            onSurface = BentoTextPrimaryLight,
            outline = BentoBorderLight
        )
    }
}

@Composable
fun LoopifyMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: ThemeAccent = ThemeAccent.PURPLE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = getBentoColorScheme(isDark, accent)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun LoopCountTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: ThemeAccent = ThemeAccent.PURPLE,
    content: @Composable () -> Unit
) {
    LoopifyMusicTheme(themeMode = themeMode, accent = accent, content = content)
}
