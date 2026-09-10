package com.example.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import com.example.model.AudioTrack
import com.example.playback.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

enum class DualSyncRole {
    NONE,
    HOST,
    CLIENT
}

enum class DualSyncConnectionState {
    DISCONNECTED,
    ADVERTISING, // Host waiting for connection
    CONNECTING,  // Client attempting connection
    CONNECTED    // Both devices connected and in sync!
}

data class DualSyncUiState(
    val connectionState: DualSyncConnectionState = DualSyncConnectionState.DISCONNECTED,
    val role: DualSyncRole = DualSyncRole.NONE,
    val connectedDeviceName: String? = null,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isSyncActive: Boolean = false,
    val statusMessage: String = "Offline Dual Listen ready"
)

/**
 * Manages 100% offline Bluetooth dual-device playback synchronization.
 * Uses standard RFCOMM BluetoothSocket without needing internet or cloud servers.
 * Highly responsive, lightweight (~50 byte JSON packets), ultra battery-friendly, and Google Play compliant.
 */
class BluetoothSyncManager(
    private val context: Context,
    private val playerManager: AudioPlayerManager
) {
    companion object {
        // Dedicated standard Bluetooth RFCOMM UUID for Loopify Dual Playback Sync
        val SYNC_UUID: UUID = UUID.fromString("9f82d54e-3c2b-4fa8-b22e-13c54d7e8901")
        const val SERVICE_NAME = "LoopifyDualSync"

        const val CMD_PLAY = "PLAY"
        const val CMD_PAUSE = "PAUSE"
        const val CMD_SEEK = "SEEK"
        const val CMD_TRACK_CHANGE = "TRACK_CHANGE"
        const val CMD_PING = "PING"
        const val CMD_PONG = "PONG"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _uiState = MutableStateFlow(DualSyncUiState())
    val uiState: StateFlow<DualSyncUiState> = _uiState.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var socketWriter: PrintWriter? = null
    private var isListening = false
    private var isInternalCommandExecuting = false

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        try {
            val paired = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            _uiState.update { it.copy(pairedDevices = paired) }
        } catch (_: Exception) {
            _uiState.update { it.copy(pairedDevices = emptyList()) }
        }
    }

    /**
     * Start hosting: Wait for a friend's phone to connect.
     * When host plays, pauses, or seeks, client synchronizes instantly.
     */
    @SuppressLint("MissingPermission")
    fun startHost() {
        disconnect()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _uiState.update {
                it.copy(
                    connectionState = DualSyncConnectionState.DISCONNECTED,
                    statusMessage = "Bluetooth is turned off"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                role = DualSyncRole.HOST,
                connectionState = DualSyncConnectionState.ADVERTISING,
                statusMessage = "Broadcasting... Tell friend to tap your phone name"
            )
        }

        scope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SYNC_UUID)
                val socket = serverSocket?.accept() // Blocking until client connects
                serverSocket?.close()
                serverSocket = null

                if (socket != null) {
                    onSocketConnected(socket, DualSyncRole.HOST, socket.remoteDevice?.name ?: "Friend's Phone")
                }
            } catch (e: Exception) {
                if (_uiState.value.connectionState == DualSyncConnectionState.ADVERTISING) {
                    _uiState.update {
                        it.copy(
                            connectionState = DualSyncConnectionState.DISCONNECTED,
                            statusMessage = "Host session cancelled or timed out"
                        )
                    }
                }
            }
        }
    }

    /**
     * Connect to host friend's device from paired list.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        disconnect()
        _uiState.update {
            it.copy(
                role = DualSyncRole.CLIENT,
                connectionState = DualSyncConnectionState.CONNECTING,
                statusMessage = "Connecting to ${device.name ?: "Friend"}..."
            )
        }

        scope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                onSocketConnected(socket, DualSyncRole.CLIENT, device.name ?: "Friend's Phone")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = DualSyncConnectionState.DISCONNECTED,
                        role = DualSyncRole.NONE,
                        statusMessage = "Could not connect to ${device.name ?: "device"}. Make sure Host is started."
                    )
                }
            }
        }
    }

    private fun onSocketConnected(socket: BluetoothSocket, role: DualSyncRole, deviceName: String) {
        activeSocket = socket
        try {
            socketWriter = PrintWriter(socket.outputStream, true)
        } catch (_: Exception) {
            disconnect()
            return
        }

        _uiState.update {
            it.copy(
                connectionState = DualSyncConnectionState.CONNECTED,
                role = role,
                connectedDeviceName = deviceName,
                isSyncActive = true,
                statusMessage = if (role == DualSyncRole.HOST) "Host: You control the music" else "Synced with $deviceName"
            )
        }

        // If we are Host and already playing a track, send the current track state to Client immediately
        if (role == DualSyncRole.HOST) {
            val currentPlayback = playerManager.state.value
            currentPlayback.currentTrack?.let { track ->
                sendTrackChange(track, currentPlayback.currentPositionMs, currentPlayback.isPlaying)
            }
        }

        startIncomingMessageLoop(socket)
    }

    private fun startIncomingMessageLoop(socket: BluetoothSocket) {
        isListening = true
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (isListening && isActive) {
                    val line = reader.readLine() ?: break
                    handleIncomingPacket(line)
                }
            } catch (_: Exception) {
                // Socket disconnected or closed
            } finally {
                disconnect()
            }
        }
    }

    private fun handleIncomingPacket(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val action = json.optString("action")
            val timestamp = json.optLong("ts", System.currentTimeMillis())

            isInternalCommandExecuting = true
            scope.launch(Dispatchers.Main) {
                try {
                    when (action) {
                        CMD_PLAY -> {
                            val pos = json.optLong("pos", -1L)
                            if (pos >= 0) {
                                playerManager.seekTo(pos)
                            }
                            playerManager.play()
                        }
                        CMD_PAUSE -> {
                            val pos = json.optLong("pos", -1L)
                            if (pos >= 0) {
                                playerManager.seekTo(pos)
                            }
                            playerManager.pause()
                        }
                        CMD_SEEK -> {
                            val pos = json.optLong("pos", 0L)
                            playerManager.seekTo(pos)
                        }
                        CMD_TRACK_CHANGE -> {
                            val trackTitle = json.optString("title", "")
                            val trackDuration = json.optLong("duration", 0L)
                            val startPos = json.optLong("pos", 0L)
                            val isPlaying = json.optBoolean("isPlaying", true)

                            // Find best matching track on local device by title or approximate duration
                            matchAndPlayLocalTrack(trackTitle, trackDuration, startPos, isPlaying)
                        }
                    }
                } finally {
                    isInternalCommandExecuting = false
                }
            }
        } catch (_: Exception) {
            isInternalCommandExecuting = false
        }
    }

    private fun matchAndPlayLocalTrack(
        targetTitle: String,
        targetDuration: Long,
        startPos: Long,
        shouldPlay: Boolean
    ) {
        if (targetTitle.isBlank()) return
        val currentTrack = playerManager.state.value.currentTrack
        if (currentTrack?.displayTitle.equals(targetTitle, ignoreCase = true)) {
            playerManager.seekTo(startPos)
            if (shouldPlay) playerManager.play() else playerManager.pause()
            return
        }

        // Search in loaded tracks
        val allTracks = com.example.LoopCountApp.instance.repository.lastLoadedTracks
        val match = allTracks.find {
            it.displayTitle.equals(targetTitle, ignoreCase = true) ||
            it.title.equals(targetTitle, ignoreCase = true)
        } ?: allTracks.find {
            // Fuzzy match: contains title or within 2s duration match
            it.displayTitle.contains(targetTitle, ignoreCase = true) ||
            (targetDuration > 0 && Math.abs(it.durationMs - targetDuration) < 2000L)
        }

        if (match != null) {
            playerManager.playTrack(match, listOf(match), startPositionMs = startPos)
            if (!shouldPlay) {
                playerManager.pause()
            }
        } else {
            // Track not on device yet
            _uiState.update {
                it.copy(
                    statusMessage = "Friend playing \"$targetTitle\" (File not on your phone yet)"
                )
            }
        }
    }

    // --- Outgoing Sync Commands ---

    fun onUserPlay(positionMs: Long) {
        if (isInternalCommandExecuting || !isSyncConnected()) return
        sendPacket(JSONObject().apply {
            put("action", CMD_PLAY)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        })
    }

    fun onUserPause(positionMs: Long) {
        if (isInternalCommandExecuting || !isSyncConnected()) return
        sendPacket(JSONObject().apply {
            put("action", CMD_PAUSE)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        })
    }

    fun onUserSeek(positionMs: Long) {
        if (isInternalCommandExecuting || !isSyncConnected()) return
        sendPacket(JSONObject().apply {
            put("action", CMD_SEEK)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        })
    }

    fun sendTrackChange(track: AudioTrack, positionMs: Long = 0L, isPlaying: Boolean = true) {
        if (isInternalCommandExecuting || !isSyncConnected()) return
        sendPacket(JSONObject().apply {
            put("action", CMD_TRACK_CHANGE)
            put("title", track.displayTitle)
            put("artist", track.displayArtist)
            put("duration", track.durationMs)
            put("pos", positionMs)
            put("isPlaying", isPlaying)
            put("ts", System.currentTimeMillis())
        })
    }

    private fun sendPacket(json: JSONObject) {
        scope.launch {
            try {
                socketWriter?.println(json.toString())
            } catch (_: Exception) {
                disconnect()
            }
        }
    }

    fun isSyncConnected(): Boolean {
        return _uiState.value.connectionState == DualSyncConnectionState.CONNECTED && activeSocket != null
    }

    fun disconnect() {
        isListening = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            socketWriter?.close()
        } catch (_: Exception) {}
        socketWriter = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null

        _uiState.update {
            it.copy(
                connectionState = DualSyncConnectionState.DISCONNECTED,
                role = DualSyncRole.NONE,
                connectedDeviceName = null,
                isSyncActive = false,
                statusMessage = "Offline Dual Listen disconnected"
            )
        }
    }
}
