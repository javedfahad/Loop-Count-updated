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

            var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }

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

            val deleteIntentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    viewModel.refreshTracks()
                }
            }

            // Sync initial permission
            LaunchedEffect(Unit) {
                val granted = checkAudioPermission()
                viewModel.setPermissionGranted(granted)
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
                // If drawer is open, close it. If on a sub-screen (NowPlaying, FolderDetail, Appearance, About), return to Home screen.
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Splash && !drawerState.isOpen) {
                    currentScreen = Screen.Home
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen is Screen.Home,
                    drawerContent = {
                        NavigationDrawerContent(
                            onNavigateToAppearance = {
                                currentScreen = Screen.Appearance
                            },
                            onNavigateToAbout = {
                                currentScreen = Screen.About
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (val screen = currentScreen) {
                                is Screen.Splash -> {
                                    SplashScreen(
                                        onSplashFinished = {
                                            currentScreen = Screen.Home
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
                                            currentScreen = Screen.NowPlaying
                                        },
                                        onOpenFolderDetail = { folder ->
                                            currentScreen = Screen.FolderDetail(folder)
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
                                                val intentSenderRequest =
                                                    IntentSenderRequest.Builder(intentSender).build()
                                                deleteIntentLauncher.launch(intentSenderRequest)
                                            }
                                        },
                                        onAddTrackToFolder = { folderId, track ->
                                            viewModel.addTrackToFolder(folderId, track)
                                        },
                                        onCreateFolderWithTrack = { folderName, track ->
                                            viewModel.createUserFolderWithTrack(folderName, track)
                                        }
                                    )
                                }

                                is Screen.FolderDetail -> {
                                    // Keep folder synced with user folders if it's a custom folder
                                    val currentFolder = uiState.userFolders.find { it.id == screen.folder.id }
                                        ?: screen.folder

                                    FolderDetailScreen(
                                        folder = currentFolder,
                                        allTracks = uiState.allTracks,
                                        currentTrack = playbackState.currentTrack,
                                        isPlaying = playbackState.isPlaying,
                                        playbackState = playbackState,
                                        onOpenNowPlaying = { currentScreen = Screen.NowPlaying },
                                        onPlayPause = { viewModel.playerManager.togglePlayPause() },
                                        onNext = { viewModel.playerManager.next() },
                                        onBack = { currentScreen = Screen.Home },
                                        onPlayTrack = { track, queue ->
                                            viewModel.playTrack(track, queue)
                                        },
                                        onPlayFolder = { tracks, minutes ->
                                            viewModel.playFolder(tracks, minutes)
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
                                        onDeleteFolder = { folderId ->
                                            viewModel.deleteUserFolder(folderId)
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
                                        onBack = { currentScreen = Screen.Home }
                                    )
                                }

                                is Screen.Appearance -> {
                                    AppearanceScreen(
                                        currentThemeMode = uiState.themeMode,
                                        currentAccent = uiState.accentColor,
                                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                                        onAccentSelected = { viewModel.setAccentColor(it) },
                                        onBack = { currentScreen = Screen.Home }
                                    )
                                }

                                is Screen.About -> {
                                    AboutScreen(
                                        onBack = { currentScreen = Screen.Home }
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
