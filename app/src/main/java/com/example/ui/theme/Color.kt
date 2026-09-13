package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian Studio Modern neutrals
val BentoBackgroundLight = Color(0xFFF8F9FC)
val BentoSurfaceLight = Color(0xFFFFFFFF)
val BentoSurfaceVariantLight = Color(0xFFEEF1F8)
val BentoBorderLight = Color(0xFFD6DBE7)
val BentoTextPrimaryLight = Color(0xFF111424)
val BentoTextSecondaryLight = Color(0xFF5A6275)

val BentoBackgroundDark = Color(0xFF0C0A14)
val BentoSurfaceDark = Color(0xFF151221)
val BentoSurfaceVariantDark = Color(0xFF1F1A30)
val BentoBorderDark = Color(0xFF2C2544)
val BentoTextPrimaryDark = Color(0xFFF3F2F8)
val BentoTextSecondaryDark = Color(0xFFA5A0B8)

// Bento Accent Containers
val BentoPurpleContainer = Color(0xFFE8E0FF)
val BentoBlueContainer = Color(0xFFD8EEFF)
val BentoGreenContainer = Color(0xFFDDF7DC)
val BentoRoseContainer = Color(0xFFFFDFE5)
val BentoYellowContainer = Color(0xFFFFF2C6)

enum class ThemeAccent(val displayName: String, val primaryColor: Color) {
    PURPLE("Studio Purple", Color(0xFF8B5CF6)),
    BLUE("Neon Cyan", Color(0xFF00B4D8)),
    GREEN("Emerald", Color(0xFF10B981)),
    ROSE("Sunset Rose", Color(0xFFF43F5E))
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}
