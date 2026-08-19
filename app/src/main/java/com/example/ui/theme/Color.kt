package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Bento Grid Warm neutrals
val BentoBackgroundLight = Color(0xFFFDF8F6)
val BentoSurfaceLight = Color(0xFFFFFFFF)
val BentoSurfaceVariantLight = Color(0xFFF3EDF7)
val BentoBorderLight = Color(0xFFCAC4D0)
val BentoTextPrimaryLight = Color(0xFF1D1B20)
val BentoTextSecondaryLight = Color(0xFF49454F)

val BentoBackgroundDark = Color(0xFF141218)
val BentoSurfaceDark = Color(0xFF1E1A22)
val BentoSurfaceVariantDark = Color(0xFF2B2831)
val BentoBorderDark = Color(0xFF49454F)
val BentoTextPrimaryDark = Color(0xFFE6E1E5)
val BentoTextSecondaryDark = Color(0xFFCAC4D0)

// Bento Accent Containers
val BentoPurpleContainer = Color(0xFFEADDFF)
val BentoBlueContainer = Color(0xFFD0E4FF)
val BentoGreenContainer = Color(0xFFE6FFD1)
val BentoRoseContainer = Color(0xFFFFDADA)
val BentoYellowContainer = Color(0xFFFFE088)

enum class ThemeAccent(val displayName: String, val primaryColor: Color) {
    PURPLE("Purple", Color(0xFF6750A4)),
    BLUE("Blue", Color(0xFF00639B)),
    GREEN("Green", Color(0xFF386B01)),
    ROSE("Rose", Color(0xFF9C4146))
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}
