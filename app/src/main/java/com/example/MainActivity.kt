package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.model.AudioTrack
import com.example.model.UserFolder
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AppearanceScreen
import com.example.ui.screens.FolderDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SupportLoopifyScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.LoopCountTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class Screen {
    object Splash : Screen()
    object Home : Screen()
    data class FolderDetail(val folder: UserFolder) : Screen()
    object NowPlaying : Screen()
    object Appearance : Screen()
    object About : Screen()
    object Support : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val playbackState by viewModel.playbackState.collectAsState()
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            var screenStack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Splash)) }
            val currentScreen = screenStack.lastOrNull() ?: Screen.Home

            fun navigateTo(screen: Screen) {
                if (screen is Screen.Splash) {
                    screenStack = listOf(Screen.Splash)
                } else if (screen is Screen.Home) {
                    screenStack = listOf(Screen.Home)
                } else {
                    if (screenStack.lastOrNull() != screen) {
                        screenStack = screenStack + screen
                    }
                }
            }

            fun navigateBack() {
                if (screenStack.size > 1) {
                    screenStack = screenStack.dropLast(1)
                } else if (screenStack.lastOrNull() !is Screen.Home) {
                    screenStack = listOf(Screen.Home)
                }
            }

            // Check permissions
            val hasAudioPermission = remember(uiState.permissionGranted) {
                checkAudioPermission()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val granted = results.values.any { it }
                viewModel.setPermissionGranted(granted)
            }

            var pendingDeleteTrack by remember { mutableStateOf<AudioTrack?>(null) }

            val deleteIntentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    pendingDeleteTrack?.let { track ->
                        viewModel.onTrackConsentDeleted(track)
                    }
                }
                pendingDeleteTrack = null
            }

            // Sync initial permission and request notification permission on Android 13+
            LaunchedEffect(Unit) {
                val granted = checkAudioPermission()
                viewModel.setPermissionGranted(granted)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            }

            // Handle messages
            LaunchedEffect(uiState.message) {
                uiState.message?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearMessage()
                }
            }

            val requestPermissions = {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
                permissionLauncher.launch(permissions)
            }

            LoopCountTheme(
                themeMode = uiState.themeMode,
                accent = uiState.accentColor
            ) {
                // System Back gesture / button handling:
                // If drawer is open, close it. If on a sub-screen, pop to the previous screen (e.g. NowPlaying -> FolderDetail -> Home).
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                BackHandler(enabled = screenStack.size > 1 && !drawerState.isOpen && currentScreen !is Screen.Splash) {
                    navigateBack()
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen is Screen.Home,
                    drawerContent = {
                        NavigationDrawerContent(
                            onNavigateToSupport = {
                                navigateTo(Screen.Support)
                            },
                            onNavigateToAppearance = {
                                navigateTo(Screen.Appearance)
                            },
                            onNavigateToAbout = {
                                navigateTo(Screen.About)
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxSize()
                    ) { _ ->
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (val screen = currentScreen) {
                                is Screen.Splash -> {
                                    SplashScreen(
                                        onSplashFinished = {
                                            navigateTo(Screen.Home)
                                        }
                                    )
                                }

                                is Screen.Home -> {
                                    HomeScreen(
                                        tracks = uiState.allTracks,
                                        userFolders = uiState.userFolders,
                                        deviceFolders = uiState.deviceFolders.associate { it.name to it.tracks },
                                        playbackState = playbackState,
                                        playerManager = viewModel.playerManager,
                                        selectedTab = uiState.selectedTab,
                                        onTabSelected = { viewModel.setSelectedTab(it) },
                                        isLoading = uiState.isLoading,
                                        hasStoragePermission = hasAudioPermission,
                                        onRequestPermission = requestPermissions,
                                        onRefreshTracks = {
                                            if (!hasAudioPermission) {
                                                requestPermissions()
                                            }
                                            viewModel.refreshTracks(showFeedback = true)
                                        },
                                        onOpenDrawer = {
                                            scope.launch { drawerState.open() }
                                        },
                                        onOpenNowPlaying = {
                                            navigateTo(Screen.NowPlaying)
                                        },
                                        onOpenFolderDetail = { folder ->
                                            viewModel.setSelectedTab(1)
                                            navigateTo(Screen.FolderDetail(folder))
                                        },
                                        onCreateFolder = { name ->
                                            viewModel.createUserFolder(name)
                                        },
                                        onRenameFolder = { id, name ->
                                            viewModel.renameUserFolder(id, name)
                                        },
                                        onDeleteFolder = { id ->
                                            viewModel.deleteUserFolder(id)
                                        },
                                        onRenameTrack = { track, title ->
                                            viewModel.renameTrack(track, title)
                                        },
                                        onDeleteTrack = { track ->
                                            viewModel.deleteTrack(track) { intentSender ->
                                                pendingDeleteTrack = track
                                                val intentSenderRequest =
                                                    IntentSenderRequest.Builder(intentSender).build()
                                                deleteIntentLauncher.launch(intentSenderRequest)
                                            }
                                        },
                                        onDeleteMultipleTracks = { tracksToDelete ->
                                            viewModel.deleteMultipleTracks(tracksToDelete) { intentSender ->
                                                val intentSenderRequest =
                                                    IntentSenderRequest.Builder(intentSender).build()
                                                deleteIntentLauncher.launch(intentSenderRequest)
                                            }
                                        },
                                        onAddTrackToFolder = { folderId, track ->
                                            viewModel.addTrackToFolder(folderId, track)
                                        },
                                        onAddMultipleTracksToFolder = { folderId, addedTracks ->
                                            viewModel.addTracksToFolder(folderId, addedTracks)
                                        },
                                        onCreateFolderWithTrack = { folderName, track ->
                                            viewModel.createUserFolderWithTrack(folderName, track)
                                        },
                                        onCreateFolderWithMultipleTracks = { folderName, initialTracks ->
                                            viewModel.createUserFolderWithTracks(folderName, initialTracks)
                                        }
                                    )
                                }

                                is Screen.FolderDetail -> {
                                    // Keep folder synced with user folders or device folders
                                    val currentFolder = when {
                                        screen.folder.id > 0 -> {
                                            uiState.userFolders.find { it.id == screen.folder.id } ?: screen.folder
                                        }
                                        else -> {
                                            uiState.deviceFolders.find { it.name.equals(screen.folder.name, ignoreCase = true) }?.let { deviceFolder ->
                                                UserFolder(id = -1, name = deviceFolder.name, tracks = deviceFolder.tracks)
                                            } ?: screen.folder
                                        }
                                    }

                                    val folderKey = if (currentFolder.id > 0) "user_${currentFolder.id}" else "device_${currentFolder.name}"

                                    FolderDetailScreen(
                                        folder = currentFolder,
                                        allTracks = uiState.allTracks,
                                        currentTrack = playbackState.currentTrack,
                                        isPlaying = playbackState.isPlaying,
                                        playbackState = playbackState,
                                        onOpenNowPlaying = { navigateTo(Screen.NowPlaying) },
                                        onPlayPause = { viewModel.playerManager.togglePlayPause() },
                                        onNext = { viewModel.playerManager.next() },
                                        onBack = { navigateBack() },
                                        onPlayTrack = { track, queue ->
                                            viewModel.playerManager.playTrack(track, queue, startPositionMs = 0L, folderKey = folderKey)
                                            navigateTo(Screen.NowPlaying)
                                        },
                                        onPlayFolder = { tracks, minutes, shuffle ->
                                            viewModel.playFolder(folderKey, tracks, minutes, shuffle)
                                            navigateTo(Screen.NowPlaying)
                                        },
                                        onResumeFolder = { tracks ->
                                            viewModel.resumeFolder(folderKey, tracks)
                                            navigateTo(Screen.NowPlaying)
                                        },
                                        onMagicRemix = { tracks ->
                                            viewModel.playMagicRemix(currentFolder.name, tracks)
                                            navigateTo(Screen.NowPlaying)
                                        },
                                        onReorder = { reordered ->
                                            if (currentFolder.id > 0) {
                                                viewModel.reorderFolderTracks(currentFolder.id, reordered)
                                            }
                                        },
                                        onAddTracks = { added ->
                                            if (currentFolder.id > 0) {
                                                viewModel.addTracksToFolder(currentFolder.id, added)
                                            }
                                        },
                                        onRemoveTrack = { trackUri ->
                                            if (currentFolder.id > 0) {
                                                viewModel.removeTrackFromFolder(currentFolder.id, trackUri)
                                            }
                                        },
                                        onRemoveMultipleTracks = { uris ->
                                            if (currentFolder.id > 0) {
                                                viewModel.removeMultipleTracksFromFolder(currentFolder.id, uris)
                                            }
                                        },
                                        onDeleteTracks = { tracksToDelete ->
                                            viewModel.deleteMultipleTracks(tracksToDelete) { intentSender ->
                                                val intentSenderRequest =
                                                    IntentSenderRequest.Builder(intentSender).build()
                                                deleteIntentLauncher.launch(intentSenderRequest)
                                            }
                                        },
                                        onDeleteFolder = { folderId ->
                                            viewModel.deleteUserFolder(folderId)
                                            navigateBack()
                                        },
                                        onRenameFolder = { folderId, newName ->
                                            viewModel.renameUserFolder(folderId, newName)
                                        }
                                    )
                                }

                                is Screen.NowPlaying -> {
                                    NowPlayingScreen(
                                        playbackState = playbackState,
                                        playerManager = viewModel.playerManager,
                                        onBack = { navigateBack() }
                                    )
                                }

                                is Screen.Appearance -> {
                                    AppearanceScreen(
                                        currentThemeMode = uiState.themeMode,
                                        currentAccent = uiState.accentColor,
                                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                                        onAccentSelected = { viewModel.setAccentColor(it) },
                                        onClearCache = { viewModel.clearAppCache() },
                                        onBack = { navigateBack() }
                                    )
                                }

                                is Screen.About -> {
                                    AboutScreen(
                                        onBack = { navigateBack() }
                                    )
                                }

                                is Screen.Support -> {
                                    SupportLoopifyScreen(
                                        onBack = { navigateBack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
