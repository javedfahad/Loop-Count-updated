package com.example.util

/**
 * Formats user entered strings into clean, proper Title Case (capitalizes each word,
 * normalizes casing, and removes extra spaces).
 * For example: "faHad Javed" -> "Fahad Javed", "mY cOoL fOLdEr" -> "My Cool Folder".
 */
fun String.toProperTitleCase(): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            if (word.length == 1) {
                word.uppercase()
            } else {
                word.substring(0, 1).uppercase() + word.substring(1).lowercase()
            }
        }
}
