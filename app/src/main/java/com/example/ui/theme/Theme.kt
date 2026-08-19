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
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoPurpleContainer,
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
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
            primary = Color(0xFF9ECAFF),
            onPrimary = Color(0xFF003258),
            primaryContainer = Color(0xFF00497D),
            onPrimaryContainer = Color(0xFFD0E4FF),
            secondary = Color(0xFFBCC7DB),
            onSecondary = Color(0xFF263140),
            secondaryContainer = Color(0xFF3C4858),
            onSecondaryContainer = Color(0xFFD8E3F8),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoBlueContainer,
            onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF51606F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD5E4F7),
            onSecondaryContainer = Color(0xFF0E1D2A),
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
            primary = Color(0xFF9CD67D),
            onPrimary = Color(0xFF1C3700),
            primaryContainer = Color(0xFF2A5000),
            onPrimaryContainer = Color(0xFFB8F397),
            secondary = Color(0xFFC0CAAB),
            onSecondary = Color(0xFF2B331E),
            secondaryContainer = Color(0xFF414A32),
            onSecondaryContainer = Color(0xFFDCE6C6),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF386B01),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoGreenContainer,
            onPrimaryContainer = Color(0xFF0C2000),
            secondary = Color(0xFF586249),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDCF8B5),
            onSecondaryContainer = Color(0xFF161E0A),
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
            primary = Color(0xFFFFB3B4),
            onPrimary = Color(0xFF5F121C),
            primaryContainer = Color(0xFF7E2A33),
            onPrimaryContainer = Color(0xFFFFDADA),
            secondary = Color(0xFFE6BDBC),
            onSecondary = Color(0xFF44292B),
            secondaryContainer = Color(0xFF5D3F40),
            onSecondaryContainer = Color(0xFFFFDADA),
            background = BentoBackgroundDark,
            surface = BentoSurfaceDark,
            surfaceVariant = BentoSurfaceVariantDark,
            onBackground = BentoTextPrimaryDark,
            onSurface = BentoTextPrimaryDark,
            outline = BentoBorderDark
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9C4146),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = BentoRoseContainer,
            onPrimaryContainer = Color(0xFF40000B),
            secondary = Color(0xFF775657),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDADA),
            onSecondaryContainer = Color(0xFF2C1516),
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
fun LoopCountTheme(
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
