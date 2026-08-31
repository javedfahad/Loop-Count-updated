package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LoopCountLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About LoopCount",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Identity Hero
            LoopCountLogo(
                size = 76.dp,
                backgroundColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "LoopCount",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Precision Counted Audio Repetition & Memorisation Player",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Version 1.0 • 100% Offline & Private",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Purpose Overview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Why LoopCount?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "LoopCount is specifically engineered for students, language learners, musicians, speech coaches, and recitation memorizers who need to repeat audio tracks an exact number of times or for a designated duration without manually pressing replay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section Header: How To Use & Core Features
            SectionHeader(title = "HOW IT WORKS & CORE FEATURES")

            Spacer(modifier = Modifier.height(12.dp))

            // Feature 1: Exact Repeat Counting
            FeatureCard(
                icon = Icons.Default.Repeat,
                title = "1. Exact Counted Repetition",
                description = "Choose quick presets (1× to 15×) or customize any target count (e.g., 33, 50, 100 times). The player displays the live countdown in real-time and automatically stops when your goal is achieved."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 2: Stop After Finish
            FeatureCard(
                icon = Icons.Default.CheckCircle,
                title = "2. Stop After Finish Mode",
                description = "Toggle 'Stop After Finish' whenever you decide you want to stop after the current iteration. Rather than cutting off the audio abruptly in the middle of a sentence or musical bar, it allows the track to reach its natural end gracefully."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 3: Custom & Device Folders
            FeatureCard(
                icon = Icons.Default.CreateNewFolder,
                title = "3. Folders & Playlists",
                description = "Group related tracks into custom playlist folders using the floating '+' button. Automatic library synchronization keeps your device storage folders organized with instant access."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 4: Folder Timers & Count Loops
            FeatureCard(
                icon = Icons.Default.Timer,
                title = "4. Folder Timers & Continuous Loops",
                description = "Set countdown timers (15 min, 30 min, 1 hour, or custom) on entire folders for study sessions, meditation, or workout routines. When the timer expires, the current track completes smoothly."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 5: Resume Audio & Folders
            FeatureCard(
                icon = Icons.Default.Replay,
                title = "5. Resume Where You Left Off",
                description = "Seamless position persistence for both individual audio tracks and folders. LoopCount automatically saves your playback timestamp and active playlist track so you can resume audiobooks, lectures, and study folders with 1 tap."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 6: Playback Speed & Pitch
            FeatureCard(
                icon = Icons.Default.Speed,
                title = "6. Speed Control & Bookmarks",
                description = "Adjust playback speeds from 0.5× to 2.0× for precise dictation or rapid review. Save timestamp bookmarks with custom notes to jump directly to key audio passages."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 7: Zero-Duplicate Smart Storage
            FeatureCard(
                icon = Icons.Default.Storage,
                title = "7. Zero-Duplicate Storage Engine",
                description = "Custom folders and playlists save lightweight pointer references without copying or duplicating your audio files. The entire app uses under 2 MB, keeping your phone storage completely free."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 8: Title Case Formatting & Magic Remix
            FeatureCard(
                icon = Icons.Default.AutoAwesome,
                title = "8. Smart Formatting & Magic Remix",
                description = "Automatic Title Case cleans up folder and track names no matter how they are typed. Enjoy playlist shuffle, continuous loop modes, and flexible audio organization."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature 9: Theme Customization & Privacy
            FeatureCard(
                icon = Icons.Default.Palette,
                title = "9. Theme Styles & Offline Privacy",
                description = "Customize dark, light, or OLED true black modes with vivid accents. LoopCount operates completely offline without data collection or tracking."
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section Header: Quick Guide Tips
            SectionHeader(title = "QUICK TIPS FOR BEST RESULTS")

            Spacer(modifier = Modifier.height(12.dp))

            GuideTipCard(
                stepNumber = "1",
                headline = "Set Your Repetition Target",
                explanation = "Tap any song to open the player. Tap the repetition dial or preset chips (1×, 3×, 5×, 10×) or enter any number via the custom counter dialog."
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideTipCard(
                stepNumber = "2",
                headline = "Organise into Custom Folders",
                explanation = "Go to the Folders tab and press the floating '+' button to create a study list. Long-press any track to quickly add it to your folder."
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideTipCard(
                stepNumber = "3",
                headline = "Resume Any Track or Folder",
                explanation = "Tap the 3-dot options menu on any audio file or folder to select 'Resume where you left', or use the 1-tap 'Resume' button in folder view."
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideTipCard(
                stepNumber = "4",
                headline = "Seamless Background Play",
                explanation = "Playback continues seamlessly in the background with lock-screen notification controls so you can listen while multitasking or with your screen turned off."
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Copyright & Proprietary Rights
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "All Rights Reserved (Proprietary) © 2026 Fahad Javed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Developed with Jetpack Compose & Material 3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun GuideTipCard(
    stepNumber: String,
    headline: String,
    explanation: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

