package com.example.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class DualSyncRole {
    NONE,
    HOST,   // DJ Host: Controls music for all connected phones
    CLIENT  // Listener: Plays in real-time sync with Host DJ
}

enum class DualSyncConnectionState {
    DISCONNECTED,
    ADVERTISING, // Host broadcasting & waiting for friends
    CONNECTING,  // Attempting connection
    CONNECTED    // Synced & active
}

data class ConnectedClientInfo(
    val id: String,
    val name: String,
    val isWifiClient: Boolean = false
)

data class DualSyncUiState(
    val connectionState: DualSyncConnectionState = DualSyncConnectionState.DISCONNECTED,
    val role: DualSyncRole = DualSyncRole.NONE,
    val connectedDeviceName: String? = null,
    val connectedDeviceNames: List<String> = emptyList(),
    val connectedDeviceCount: Int = 0,
    val maxDevices: Int = 15,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isSyncActive: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val missingTrackTitle: String? = null,
    val missingClientName: String? = null,
    val statusMessage: String = "Offline DJ Dual Listen ready"
)

/**
 * Manages 100% offline multi-device party playback synchronization (DJ Mode - up to 15 phones).
 * Uses Bluetooth RFCOMM + Local Wi-Fi / Hotspot sockets simultaneously so friends can connect
 * seamlessly without needing internet or cloud servers.
 */
class BluetoothSyncManager(
    private val context: Context,
    private val playerManager: AudioPlayerManager
) {
    companion object {
        val SYNC_UUID: UUID = UUID.fromString("9f82d54e-3c2b-4fa8-b22e-13c54d7e8901")
        const val SERVICE_NAME = "TunyMusicDualSync"
        const val TCP_SYNC_PORT = 8890
        const val MAX_PARTY_DEVICES = 15

        const val CMD_PLAY = "PLAY"
        const val CMD_PAUSE = "PAUSE"
        const val CMD_STOP = "STOP"
        const val CMD_SEEK = "SEEK"
        const val CMD_TRACK_CHANGE = "TRACK_CHANGE"
        const val CMD_MISSING_TRACK = "MISSING_TRACK"
        const val CMD_REQUEST_TOGGLE = "REQUEST_TOGGLE"
    }

    private class ClientSession(
        val id: String,
        val name: String,
        val btSocket: BluetoothSocket? = null,
        val tcpSocket: Socket? = null,
        val writer: PrintWriter
    )

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _uiState = MutableStateFlow(DualSyncUiState())
    val uiState: StateFlow<DualSyncUiState> = _uiState.asStateFlow()

    private var btServerSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null

    // Multi-device client connections for Host (up to 15 devices)
    private val connectedClients = CopyOnWriteArrayList<ClientSession>()

    // For Client mode (single uplink to Host)
    private var clientBtSocket: BluetoothSocket? = null
    private var clientTcpSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    private var isListening = false
    private var isInternalCommandExecuting = false

    init {
        updateBluetoothState()
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun updateBluetoothState() {
        val isEnabled = bluetoothAdapter?.isEnabled == true
        _uiState.update { it.copy(isBluetoothEnabled = isEnabled) }
        if (isEnabled) {
            refreshPairedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleBluetooth(openSettingsFallback: () -> Unit) {
        val adapter = bluetoothAdapter ?: run {
            openSettingsFallback()
            return
        }
        try {
            if (adapter.isEnabled) {
                // On modern Android (API 33+), programmatic disable is restricted, open settings if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    openSettingsFallback()
                } else {
                    @Suppress("DEPRECATION")
                    val ok = adapter.disable()
                    if (!ok) openSettingsFallback()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    openSettingsFallback()
                } else {
                    @Suppress("DEPRECATION")
                    val ok = adapter.enable()
                    if (!ok) openSettingsFallback()
                }
            }
        } catch (_: Exception) {
            openSettingsFallback()
        }
        updateBluetoothState()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        try {
            val paired = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            _uiState.update {
                it.copy(
                    pairedDevices = paired,
                    isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(pairedDevices = emptyList()) }
        }
    }

    /**
     * Start hosting DJ session: Accept up to 15 phones simultaneously.
     * Host broadcasts play, pause, seek, stop, and track changes to everyone.
     */
    @SuppressLint("MissingPermission")
    fun startHost() {
        disconnect()
        updateBluetoothState()

        val isBtOn = bluetoothAdapter?.isEnabled == true
        _uiState.update {
            it.copy(
                role = DualSyncRole.HOST,
                connectionState = DualSyncConnectionState.ADVERTISING,
                connectedDeviceNames = emptyList(),
                connectedDeviceCount = 0,
                statusMessage = if (isBtOn)
                    "DJ Host Ready • Waiting for up to 15 devices to join..."
                else
                    "DJ Host Ready on Wi-Fi/Hotspot (Turn ON Bluetooth to also allow Bluetooth friends)"
            )
        }

        isListening = true

        // 1. Listen on Bluetooth RFCOMM if Bluetooth is on
        if (isBtOn && bluetoothAdapter != null) {
            scope.launch {
                try {
                    btServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SYNC_UUID)
                    while (isListening && isActive && connectedClients.size < MAX_PARTY_DEVICES) {
                        val socket = btServerSocket?.accept() ?: break
                        handleIncomingHostClient(socket = socket)
                    }
                } catch (_: Exception) {
                    // Closed or interrupted
                }
            }
        }

        // 2. Listen on TCP socket for Wi-Fi / Hotspot clients (up to 15 devices)
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(TCP_SYNC_PORT).apply {
                    reuseAddress = true
                }
                while (isListening && isActive && connectedClients.size < MAX_PARTY_DEVICES) {
                    val socket = tcpServerSocket?.accept() ?: break
                    handleIncomingTcpClient(socket)
                }
            } catch (_: Exception) {
                // Closed or interrupted
            }
        }
    }

    private fun handleIncomingHostClient(socket: BluetoothSocket) {
        if (connectedClients.size >= MAX_PARTY_DEVICES) {
            try { socket.close() } catch (_: Exception) {}
            return
        }

        @SuppressLint("MissingPermission")
        val deviceName = try {
            socket.remoteDevice?.name ?: "Friend's Phone"
        } catch (_: Exception) {
            "Friend's Phone"
        }

        try {
            val writer = PrintWriter(socket.outputStream, true)
            val session = ClientSession(
                id = UUID.randomUUID().toString(),
                name = deviceName,
                btSocket = socket,
                writer = writer
            )
            connectedClients.add(session)
            updateHostConnectedState()

            // Push current playing track to new friend immediately
            pushCurrentTrackToClient(writer)

            // Start reader loop for this client
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))
                    while (isListening && isActive) {
                        val line = reader.readLine() ?: break
                        handleHostIncomingMessage(session, line)
                    }
                } catch (_: Exception) {
                    // Client disconnected
                } finally {
                    removeClientSession(session)
                }
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleIncomingTcpClient(socket: Socket) {
        if (connectedClients.size >= MAX_PARTY_DEVICES) {
            try { socket.close() } catch (_: Exception) {}
            return
        }

        val deviceName = "Wi-Fi Device (${socket.inetAddress.hostAddress})"

        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            val session = ClientSession(
                id = UUID.randomUUID().toString(),
                name = deviceName,
                tcpSocket = socket,
                writer = writer
            )
            connectedClients.add(session)
            updateHostConnectedState()

            pushCurrentTrackToClient(writer)

            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    while (isListening && isActive) {
                        val line = reader.readLine() ?: break
                        handleHostIncomingMessage(session, line)
                    }
                } catch (_: Exception) {
                    // Disconnected
                } finally {
                    removeClientSession(session)
                }
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun removeClientSession(session: ClientSession) {
        connectedClients.remove(session)
        try { session.btSocket?.close() } catch (_: Exception) {}
        try { session.tcpSocket?.close() } catch (_: Exception) {}
        try { session.writer.close() } catch (_: Exception) {}
        updateHostConnectedState()
    }

    private fun updateHostConnectedState() {
        val names = connectedClients.map { it.name }
        val count = connectedClients.size
        _uiState.update {
            it.copy(
                connectionState = if (count > 0) DualSyncConnectionState.CONNECTED else DualSyncConnectionState.ADVERTISING,
                role = DualSyncRole.HOST,
                connectedDeviceName = names.firstOrNull(),
                connectedDeviceNames = names,
                connectedDeviceCount = count,
                isSyncActive = count > 0,
                statusMessage = if (count > 0)
                    "DJ Host: In Sync with $count phone(s) • You control the music"
                else
                    "Broadcasting as Host DJ... Waiting for friends to join"
            )
        }
    }

    private fun pushCurrentTrackToClient(writer: PrintWriter) {
        val currentPlayback = playerManager.state.value
        currentPlayback.currentTrack?.let { track ->
            scope.launch {
                try {
                    val json = JSONObject().apply {
                        put("action", CMD_TRACK_CHANGE)
                        put("title", track.displayTitle)
                        put("artist", track.displayArtist)
                        put("duration", track.durationMs)
                        put("pos", currentPlayback.currentPositionMs)
                        put("isPlaying", currentPlayback.isPlaying)
                        put("ts", System.currentTimeMillis())
                    }
                    writer.println(json.toString())
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleHostIncomingMessage(session: ClientSession, jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            when (json.optString("action")) {
                CMD_MISSING_TRACK -> {
                    val title = json.optString("title")
                    val client = json.optString("client", session.name)
                    _uiState.update {
                        it.copy(
                            missingTrackTitle = title,
                            missingClientName = client,
                            statusMessage = "⚠️ $client is missing \"$title\""
                        )
                    }
                }
                CMD_REQUEST_TOGGLE -> {
                    // Client asked DJ to toggle playback
                    scope.launch(Dispatchers.Main) {
                        playerManager.togglePlayPause()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Connect to host friend's device via Bluetooth.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        disconnect()
        val deviceName = try { device.name ?: "DJ Friend" } catch (_: Exception) { "DJ Friend" }
        _uiState.update {
            it.copy(
                role = DualSyncRole.CLIENT,
                connectionState = DualSyncConnectionState.CONNECTING,
                statusMessage = "Connecting to $deviceName..."
            )
        }

        scope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                setupClientConnection(btSocket = socket, tcpSocket = null, hostName = deviceName)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = DualSyncConnectionState.DISCONNECTED,
                        role = DualSyncRole.NONE,
                        statusMessage = "Could not connect to $deviceName. Make sure Host started DJ session."
                    )
                }
            }
        }
    }

    /**
     * Connect to host friend via local Wi-Fi / Hotspot IP address on port 8890 (or custom port).
     */
    fun connectToHostIp(hostIp: String, hostLabel: String = "DJ Host") {
        disconnect()
        _uiState.update {
            it.copy(
                role = DualSyncRole.CLIENT,
                connectionState = DualSyncConnectionState.CONNECTING,
                statusMessage = "Connecting to $hostLabel..."
            )
        }

        val parsedHost = if (hostIp.contains(":")) hostIp.substringBefore(":") else hostIp
        val parsedPort = if (hostIp.contains(":")) {
            hostIp.substringAfter(":").filter { it.isDigit() }.toIntOrNull() ?: TCP_SYNC_PORT
        } else {
            TCP_SYNC_PORT
        }

        scope.launch {
            try {
                val socket = Socket(parsedHost.trim(), parsedPort)
                setupClientConnection(btSocket = null, tcpSocket = socket, hostName = hostLabel)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = DualSyncConnectionState.DISCONNECTED,
                        role = DualSyncRole.NONE,
                        statusMessage = "Could not connect to $hostLabel on Wi-Fi."
                    )
                }
            }
        }
    }

    private fun setupClientConnection(
        btSocket: BluetoothSocket?,
        tcpSocket: Socket?,
        hostName: String
    ) {
        clientBtSocket = btSocket
        clientTcpSocket = tcpSocket

        val outStream = btSocket?.outputStream ?: tcpSocket?.getOutputStream() ?: run {
            disconnect()
            return
        }
        clientWriter = PrintWriter(outStream, true)

        _uiState.update {
            it.copy(
                connectionState = DualSyncConnectionState.CONNECTED,
                role = DualSyncRole.CLIENT,
                connectedDeviceName = hostName,
                connectedDeviceNames = listOf(hostName),
                connectedDeviceCount = 1,
                isSyncActive = true,
                statusMessage = "Synced with DJ $hostName • Enjoy the mix!"
            )
        }

        isListening = true
        scope.launch {
            val inStream = btSocket?.inputStream ?: tcpSocket?.getInputStream() ?: return@launch
            try {
                val reader = BufferedReader(InputStreamReader(inStream))
                while (isListening && isActive) {
                    val line = reader.readLine() ?: break
                    handleClientIncomingMessage(line)
                }
            } catch (_: Exception) {
                // Uplink disconnected
            } finally {
                disconnect()
            }
        }
    }

    private fun handleClientIncomingMessage(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val action = json.optString("action")

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
                        CMD_STOP -> {
                            playerManager.stop()
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
            _uiState.update { it.copy(missingTrackTitle = null) }
            return
        }

        // Search in all loaded tracks
        val allTracks = com.example.LoopCountApp.instance.repository.lastLoadedTracks
        val match = allTracks.find {
            it.displayTitle.equals(targetTitle, ignoreCase = true) ||
            it.title.equals(targetTitle, ignoreCase = true)
        } ?: allTracks.find {
            it.displayTitle.contains(targetTitle, ignoreCase = true) ||
            (targetDuration > 0 && Math.abs(it.durationMs - targetDuration) < 2000L)
        }

        if (match != null) {
            _uiState.update { it.copy(missingTrackTitle = null) }
            playerManager.playTrack(match, listOf(match), startPositionMs = startPos)
            if (!shouldPlay) {
                playerManager.pause()
            }
        } else {
            // Track not on client phone yet
            _uiState.update {
                it.copy(
                    missingTrackTitle = targetTitle,
                    statusMessage = "DJ is playing \"$targetTitle\" (Not on your phone yet)"
                )
            }
            // Send feedback to DJ so DJ can share it via Hotspot
            sendMissingTrackAlert(targetTitle)
        }
    }

    private fun sendMissingTrackAlert(title: String) {
        val myName = try {
            bluetoothAdapter?.name ?: "Listener"
        } catch (_: Exception) {
            "Listener"
        }
        sendPacket(JSONObject().apply {
            put("action", CMD_MISSING_TRACK)
            put("title", title)
            put("client", myName)
        })
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

    fun onUserStop() {
        if (isInternalCommandExecuting || !isSyncConnected()) return
        sendPacket(JSONObject().apply {
            put("action", CMD_STOP)
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

    fun requestToggleFromHost() {
        sendPacket(JSONObject().apply {
            put("action", CMD_REQUEST_TOGGLE)
            put("ts", System.currentTimeMillis())
        })
    }

    private fun sendPacket(json: JSONObject) {
        val packet = json.toString()
        scope.launch {
            if (_uiState.value.role == DualSyncRole.HOST) {
                // Broadcast to all connected party clients (up to 15)
                val deadSessions = mutableListOf<ClientSession>()
                for (session in connectedClients) {
                    try {
                        session.writer.println(packet)
                    } catch (_: Exception) {
                        deadSessions.add(session)
                    }
                }
                for (dead in deadSessions) {
                    removeClientSession(dead)
                }
            } else if (_uiState.value.role == DualSyncRole.CLIENT) {
                try {
                    clientWriter?.println(packet)
                } catch (_: Exception) {
                    disconnect()
                }
            }
        }
    }

    fun isSyncConnected(): Boolean {
        val state = _uiState.value
        return state.connectionState == DualSyncConnectionState.CONNECTED &&
                (connectedClients.isNotEmpty() || clientWriter != null)
    }

    fun clearMissingTrackAlert() {
        _uiState.update { it.copy(missingTrackTitle = null, missingClientName = null) }
    }

    fun disconnect() {
        isListening = false

        try { btServerSocket?.close() } catch (_: Exception) {}
        btServerSocket = null

        try { tcpServerSocket?.close() } catch (_: Exception) {}
        tcpServerSocket = null

        for (client in connectedClients) {
            try { client.btSocket?.close() } catch (_: Exception) {}
            try { client.tcpSocket?.close() } catch (_: Exception) {}
            try { client.writer.close() } catch (_: Exception) {}
        }
        connectedClients.clear()

        try { clientBtSocket?.close() } catch (_: Exception) {}
        clientBtSocket = null

        try { clientTcpSocket?.close() } catch (_: Exception) {}
        clientTcpSocket = null

        try { clientWriter?.close() } catch (_: Exception) {}
        clientWriter = null

        _uiState.update {
            it.copy(
                connectionState = DualSyncConnectionState.DISCONNECTED,
                role = DualSyncRole.NONE,
                connectedDeviceName = null,
                connectedDeviceNames = emptyList(),
                connectedDeviceCount = 0,
                isSyncActive = false,
                missingTrackTitle = null,
                missingClientName = null,
                statusMessage = "Offline DJ Dual Listen disconnected"
            )
        }
    }
}
