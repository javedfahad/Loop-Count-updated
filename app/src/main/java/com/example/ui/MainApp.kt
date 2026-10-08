package com.example.ui

import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.platform.LocalContext
import com.example.model.AudioTrack
import com.example.model.UserFolder
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.dialogs.DualListenBottomSheet
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AppearanceScreen
import com.example.ui.screens.FolderDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SupportLoopifyScreen
import com.example.ui.splash.SplashScreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class Screen {
    SPLASH,
    HOME,
    FOLDER_DETAIL,
    APPEARANCE,
    ABOUT,
    SUPPORT,
    NOW_PLAYING
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit = {},
    onDeleteConsentRequired: (IntentSender) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var showDualListenBottomSheet by remember { mutableStateOf(false) }

    // Display feedback toast messages from viewModel
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val activeUserFolder = remember(uiState.userFolders, selectedFolderId) {
        uiState.userFolders.find { it.id == selectedFolderId } ?: UserFolder(
            id = selectedFolderId ?: 0L,
            name = "Folder"
        )
    }

    // Modal Bottom Sheet for Dual Listen Party [Beta]
    if (showDualListenBottomSheet) {
        DualListenBottomSheet(
            syncManager = viewModel.bluetoothSyncManager,
            onStopMusic = { viewModel.playerManager.stop() },
            onPlayPauseMusic = { viewModel.playerManager.togglePlayPause() },
            onDismiss = { showDualListenBottomSheet = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.SPLASH -> {
                    SplashScreen(
                        onSplashFinished = {
                            currentScreen = Screen.HOME
                        }
                    )
                }

                Screen.HOME -> {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen,
                        drawerContent = {
                            NavigationDrawerContent(
                                onNavigateToSupport = {
                                    scope.launch { drawerState.close() }
                                    currentScreen = Screen.SUPPORT
                                },
                                onNavigateToAppearance = {
                                    scope.launch { drawerState.close() }
                                    currentScreen = Screen.APPEARANCE
                                },
                                onNavigateToAbout = {
                                    scope.launch { drawerState.close() }
                                    currentScreen = Screen.ABOUT
                                },
                                onOpenDualListen = {
                                    scope.launch { drawerState.close() }
                                    showDualListenBottomSheet = true
                                },
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        HomeScreen(
                            tracks = uiState.allTracks,
                            userFolders = uiState.userFolders,
                            deviceFolders = uiState.deviceFolders.associate { it.name to it.tracks },
                            playbackState = playbackState,
                            playerManager = viewModel.playerManager,
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            isLoading = uiState.isLoading,
                            hasStoragePermission = uiState.permissionGranted,
                            onRequestPermission = onRequestPermissions,
                            onRefreshTracks = { viewModel.refreshTracks(showFeedback = true) },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenNowPlaying = { currentScreen = Screen.NOW_PLAYING },
                            onOpenFolderDetail = { folder ->
                                selectedFolderId = folder.id
                                currentScreen = Screen.FOLDER_DETAIL
                            },
                            onCreateFolder = { name -> viewModel.createUserFolder(name) },
                            onRenameFolder = { id, name -> viewModel.renameUserFolder(id, name) },
                            onDeleteFolder = { id -> viewModel.deleteUserFolder(id) },
                            onRenameTrack = { track, newTitle -> viewModel.renameTrack(track, newTitle) },
                            onDeleteTrack = { track -> viewModel.deleteTrack(track, onDeleteConsentRequired) },
                            onDeleteMultipleTracks = { tracks -> viewModel.deleteMultipleTracks(tracks, onDeleteConsentRequired) },
                            onAddTrackToFolder = { folderId, track -> viewModel.addTrackToFolder(folderId, track) },
                            onAddMultipleTracksToFolder = { folderId, tracks -> viewModel.addTracksToFolder(folderId, tracks) },
                            onCreateFolderWithTrack = { name, track -> viewModel.createUserFolderWithTrack(name, track) },
                            onCreateFolderWithMultipleTracks = { name, tracks -> viewModel.createFolderWithMultipleTracks(name, tracks) },
                            onOpenDualListen = { showDualListenBottomSheet = true }
                        )
                    }
                }

                Screen.FOLDER_DETAIL -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    FolderDetailScreen(
                        folder = activeUserFolder,
                        allTracks = uiState.allTracks,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        playbackState = playbackState,
                        onOpenNowPlaying = { currentScreen = Screen.NOW_PLAYING },
                        onPlayPause = { viewModel.playerManager.togglePlayPause() },
                        onNext = { viewModel.playerManager.next() },
                        onCloseMiniPlayer = { viewModel.playerManager.dismissPlayer() },
                        onBack = { currentScreen = Screen.HOME },
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlayFolder = { tracks, timerMinutes, shuffle ->
                            viewModel.playFolder(
                                folderKey = "user_${activeUserFolder.id}",
                                tracks = tracks,
                                timerMinutes = timerMinutes,
                                shuffle = shuffle
                            )
                        },
                        onResumeFolder = { tracks ->
                            viewModel.resumeFolder(
                                folderKey = "user_${activeUserFolder.id}",
                                tracks = tracks
                            )
                        },
                        onMagicRemix = { tracks ->
                            viewModel.playMagicRemix(
                                folderName = activeUserFolder.name,
                                tracks = tracks
                            )
                        },
                        onReorder = { reordered ->
                            viewModel.reorderFolderTracks(activeUserFolder.id, reordered)
                        },
                        onAddTracks = { tracks ->
                            viewModel.addTracksToFolder(activeUserFolder.id, tracks)
                        },
                        onRemoveTrack = { trackUri ->
                            viewModel.removeTrackFromFolder(activeUserFolder.id, trackUri)
                        },
                        onRemoveMultipleTracks = { uris ->
                            viewModel.removeMultipleTracksFromFolder(activeUserFolder.id, uris)
                        },
                        onDeleteTracks = { tracks ->
                            viewModel.deleteMultipleTracks(tracks, onDeleteConsentRequired)
                        },
                        onDeleteFolder = { folderId ->
                            viewModel.deleteUserFolder(folderId)
                            currentScreen = Screen.HOME
                        },
                        onRenameFolder = { folderId, name ->
                            viewModel.renameUserFolder(folderId, name)
                        },
                        userFolders = uiState.userFolders,
                        onAddMultipleTracksToFolder = { folderId, tracks ->
                            viewModel.addTracksToFolder(folderId, tracks)
                        },
                        onCreateFolderWithMultipleTracks = { name, tracks ->
                            viewModel.createFolderWithMultipleTracks(name, tracks)
                        }
                    )
                }

                Screen.APPEARANCE -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    AppearanceScreen(
                        currentThemeMode = uiState.themeMode,
                        currentAccent = uiState.accentColor,
                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                        onAccentSelected = { viewModel.setAccentColor(it) },
                        onClearCache = { viewModel.clearAppCache() },
                        onBack = { currentScreen = Screen.HOME }
                    )
                }

                Screen.ABOUT -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    AboutScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                }

                Screen.SUPPORT -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    SupportLoopifyScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                }

                Screen.NOW_PLAYING -> {
                    BackHandler {
                        currentScreen = Screen.HOME
                    }
                    NowPlayingScreen(
                        playbackState = playbackState,
                        playerManager = viewModel.playerManager,
                        onOpenDualListen = {
                            showDualListenBottomSheet = true
                        },
                        onBack = {
                            currentScreen = Screen.HOME
                        }
                    )
                }
            }
        }
    }
}
