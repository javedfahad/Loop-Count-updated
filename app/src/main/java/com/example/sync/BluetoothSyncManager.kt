package com.example.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.example.model.AudioTrack
import com.example.playback.AudioPlayerManager
import com.example.transfer.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class DualSyncRole {
    NONE,
    HOST,   // Party Host: Source of audio, controls music for all listeners
    CLIENT  // Listener: Streams live audio from Host phone, synchronized in real time
}

enum class DualSyncConnectionState {
    DISCONNECTED,
    ADVERTISING, // Host session ready & broadcasting
    CONNECTING,  // Attempting to join Host party
    CONNECTED,   // Active party
    ERROR        // Connection failed or rejected
}

data class ConnectedListener(
    val id: String,
    val name: String,
    val ip: String,
    val joinedAt: Long = System.currentTimeMillis()
)

data class DualSyncUiState(
    val connectionState: DualSyncConnectionState = DualSyncConnectionState.DISCONNECTED,
    val role: DualSyncRole = DualSyncRole.NONE,
    val partyName: String = "",
    val hostIp: String = "",
    val hostPort: Int = BluetoothSyncManager.TCP_CONTROL_PORT,
    val httpPort: Int = BluetoothSyncManager.HTTP_STREAM_PORT,
    val sessionId: String = "",
    val sessionToken: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val qrPayload: String = "",
    val connectedDeviceNames: List<String> = emptyList(),
    val connectedListeners: List<ConnectedListener> = emptyList(),
    val connectedDeviceName: String? = null,
    val connectedDeviceCount: Int = 0,
    val maxDevices: Int = BluetoothSyncManager.MAX_LISTENERS,
    val isSyncActive: Boolean = false,
    val isWifiOrHotspotReady: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val currentStreamingTitle: String? = null,
    val currentStreamingArtist: String? = null,
    val isHostMusicPlaying: Boolean = false,
    val missingTrackTitle: String? = null,
    val missingClientName: String? = null,
    val statusMessage: String = "Dual Listen Offline Party ready",
    val errorMessage: String? = null
)

/**
 * Dual Listen Party Manager (100% Offline Multi-Phone Listening Session).
 * 
 * Architecture:
 * - 1 Host Phone (Source of audio) + Up to 6 Connected Listeners = Maximum 7 phones total.
 * - Streams actual audio file bytes from Host to Listeners over local Wi-Fi / Hotspot via HTTP.
 * - Listeners buffer and play in temporary memory (Zero permanent downloads, zero storage copies).
 * - Real-time synchronization for Play, Pause, Seek, and Track Change with latency compensation.
 * - Smooth Late Joining: New listeners jump straight into the current song position (e.g. 01:30).
 * - Simple QR Code pairing: Generates a temporary random session ID and auth token per party.
 */
class BluetoothSyncManager(
    private val context: Context,
    val playerManager: AudioPlayerManager
) {
    companion object {
        const val TAG = "DualListenManager"
        const val TCP_CONTROL_PORT = 8890
        const val HTTP_STREAM_PORT = 8892
        const val MAX_LISTENERS = 6 // 1 Host + 6 Listeners = 7 phones maximum

        // Control Protocol Commands
        const val CMD_JOIN = "JOIN"
        const val CMD_JOIN_OK = "JOIN_OK"
        const val CMD_REJECT = "REJECT"
        const val CMD_PLAY = "PLAY"
        const val CMD_PAUSE = "PAUSE"
        const val CMD_STOP = "STOP"
        const val CMD_SEEK = "SEEK"
        const val CMD_TRACK_CHANGE = "TRACK_CHANGE"
        const val CMD_SYNC_HEARTBEAT = "SYNC_HEARTBEAT"
        const val CMD_PARTY_ENDED = "PARTY_ENDED"
        const val CMD_LEAVE = "LEAVE"
        const val CMD_LISTENER_COUNT_UPDATE = "LISTENER_COUNT_UPDATE"
    }

    private class HostClientSession(
        val id: String,
        val name: String,
        val socket: Socket,
        val writer: PrintWriter
    )

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _uiState = MutableStateFlow(DualSyncUiState())
    val uiState: StateFlow<DualSyncUiState> = _uiState.asStateFlow()

    // Dedicated HTTP Audio Streaming Server
    private val audioStreamServer = DualListenAudioStreamServer(context, HTTP_STREAM_PORT)

    // Host Sockets & Sessions
    private var hostServerSocket: ServerSocket? = null
    private val connectedClients = CopyOnWriteArrayList<HostClientSession>()
    private var heartbeatJob: Job? = null

    // Listener (Client) Uplink
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null
    private var clientReaderJob: Job? = null

    @Volatile
    private var isPartyActive = false

    @Volatile
    private var isInternalCommandExecuting = false

    init {
        updateBluetoothState()
    }

    fun isHost(): Boolean = _uiState.value.role == DualSyncRole.HOST
    fun isClient(): Boolean = _uiState.value.role == DualSyncRole.CLIENT
    fun isSyncConnected(): Boolean = _uiState.value.connectionState == DualSyncConnectionState.CONNECTED

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun updateBluetoothState() {
        val isEnabled = bluetoothAdapter?.isEnabled == true
        _uiState.update { it.copy(isBluetoothEnabled = isEnabled) }
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

    fun getUserDeviceName(): String {
        val model = Build.MODEL ?: "Phone"
        val manufacturer = Build.MANUFACTURER ?: ""
        return if (manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true)) {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        } else {
            model
        }
    }

    // =========================================================================
    // HOST FLOW: CREATE PARTY & STREAM AUDIO
    // =========================================================================

    /**
     * Creates a new Dual Listen party session as Host.
     * Generates a temporary session token, starts the HTTP stream server and TCP control server,
     * and produces the QR code for up to 6 friends to scan and join.
     */
    fun createParty() {
        disconnect()

        val hostIp = NetworkUtils.getLocalIpAddress(context)
        val isLocalIpValid = hostIp.isNotBlank() && hostIp != "127.0.0.1" && hostIp != "0.0.0.0"

        val sessionId = UUID.randomUUID().toString().take(8)
        val sessionToken = UUID.randomUUID().toString().replace("-", "").take(12)
        val partyName = "${getUserDeviceName()}'s Party"

        val qrPayloadJson = JSONObject().apply {
            put("app", "TunyMusicDual")
            put("v", 2)
            put("ip", hostIp)
            put("port", TCP_CONTROL_PORT)
            put("httpPort", HTTP_STREAM_PORT)
            put("session", sessionId)
            put("token", sessionToken)
            put("name", partyName)
        }.toString()

        val qrBitmap = try {
            NetworkUtils.generateQrCodeBitmap(qrPayloadJson, 600)
        } catch (e: Exception) {
            null
        }

        _uiState.update {
            it.copy(
                role = DualSyncRole.HOST,
                connectionState = DualSyncConnectionState.ADVERTISING,
                partyName = partyName,
                hostIp = hostIp,
                hostPort = TCP_CONTROL_PORT,
                httpPort = HTTP_STREAM_PORT,
                sessionId = sessionId,
                sessionToken = sessionToken,
                qrCodeBitmap = qrBitmap,
                qrPayload = qrPayloadJson,
                connectedListeners = emptyList(),
                connectedDeviceNames = emptyList(),
                connectedDeviceCount = 0,
                isSyncActive = false,
                isWifiOrHotspotReady = isLocalIpValid,
                errorMessage = null,
                statusMessage = if (isLocalIpValid) "Party Ready • Scan QR to join" else "Please turn on Hotspot or Wi-Fi"
            )
        }

        isPartyActive = true

        // 1. Start HTTP Audio Streaming Server with current track
        val currentTrack = playerManager.state.value.currentTrack
        audioStreamServer.start(sessionId, sessionToken, currentTrack)

        // 2. Start TCP Control Server to handle listener handshakes & commands
        scope.launch {
            try {
                hostServerSocket = ServerSocket(TCP_CONTROL_PORT).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Host TCP Control Server started on port $TCP_CONTROL_PORT")

                while (isPartyActive && isActive) {
                    val socket = hostServerSocket?.accept() ?: break
                    handleIncomingListenerConnection(socket, sessionId, sessionToken, partyName)
                }
            } catch (e: Exception) {
                if (isPartyActive) {
                    Log.e(TAG, "Host server socket exception: ${e.message}")
                }
            }
        }

        // 3. Start Periodic Sync Heartbeat (keeps listeners tightly synchronized)
        startHostSyncHeartbeat()
    }

    private fun handleIncomingListenerConnection(
        socket: Socket,
        activeSessionId: String,
        activeToken: String,
        partyName: String
    ) {
        scope.launch {
            try {
                socket.tcpNoDelay = true
                socket.soTimeout = 10_000 // 10s handshake timeout
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val handshakeLine = reader.readLine()
                if (handshakeLine.isNullOrBlank()) {
                    socket.close()
                    return@launch
                }

                val handshakeJson = JSONObject(handshakeLine)
                if (handshakeJson.optString("action") != CMD_JOIN) {
                    socket.close()
                    return@launch
                }

                val reqSession = handshakeJson.optString("session")
                val reqToken = handshakeJson.optString("token")
                val clientDeviceName = handshakeJson.optString("name", "Friend's Phone")

                // Security & Session Validation
                if (reqSession != activeSessionId || reqToken != activeToken) {
                    val reject = JSONObject().apply {
                        put("action", CMD_REJECT)
                        put("reason", "EXPIRED")
                        put("message", "Invalid or expired party QR code.")
                    }
                    writer.println(reject.toString())
                    socket.close()
                    return@launch
                }

                // Check 6 listener limit (1 Host + 6 Listeners = 7 total maximum)
                if (connectedClients.size >= MAX_LISTENERS) {
                    val reject = JSONObject().apply {
                        put("action", CMD_REJECT)
                        put("reason", "FULL")
                        put("message", "Party is full — maximum 6 listeners.")
                    }
                    writer.println(reject.toString())
                    socket.close()
                    return@launch
                }

                // Reset timeout for persistent connection
                socket.soTimeout = 0

                val clientId = UUID.randomUUID().toString()
                val session = HostClientSession(
                    id = clientId,
                    name = clientDeviceName,
                    socket = socket,
                    writer = writer
                )
                connectedClients.add(session)
                updateHostListenersState()

                // Prepare JOIN_OK with current track & playback position for late joining
                val currentPlayback = playerManager.state.value
                val curTrack = currentPlayback.currentTrack
                val trackObj = if (curTrack != null) {
                    val hostIp = _uiState.value.hostIp
                    val streamUrl = "http://$hostIp:$HTTP_STREAM_PORT/audio?session=$activeSessionId&token=$activeToken"
                    JSONObject().apply {
                        put("title", curTrack.displayTitle)
                        put("artist", curTrack.displayArtist)
                        put("duration", curTrack.durationMs)
                        put("streamUrl", streamUrl)
                        put("pos", currentPlayback.currentPositionMs)
                        put("isPlaying", currentPlayback.isPlaying)
                        put("ts", System.currentTimeMillis())
                    }
                } else null

                val joinOk = JSONObject().apply {
                    put("action", CMD_JOIN_OK)
                    put("partyName", partyName)
                    put("listenerCount", connectedClients.size)
                    put("maxListeners", MAX_LISTENERS)
                    if (trackObj != null) {
                        put("currentTrack", trackObj)
                    }
                }
                writer.println(joinOk.toString())

                // Broadcast listener count update to other connected phones
                broadcastListenerCount()

                // Reader loop for this connected listener
                while (isPartyActive && isActive) {
                    val line = reader.readLine() ?: break
                    handleHostClientMessage(session, line)
                }
            } catch (_: Exception) {
                // Client disconnected
            } finally {
                removeHostClientSession(socket)
            }
        }
    }

    private fun handleHostClientMessage(session: HostClientSession, message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("action")) {
                CMD_LEAVE -> {
                    session.socket.close()
                }
            }
        } catch (_: Exception) {}
    }

    private fun removeHostClientSession(socket: Socket) {
        val found = connectedClients.firstOrNull { it.socket == socket }
        if (found != null) {
            connectedClients.remove(found)
            try { found.writer.close() } catch (_: Exception) {}
            try { found.socket.close() } catch (_: Exception) {}
            updateHostListenersState()
            broadcastListenerCount()
        }
    }

    private fun updateHostListenersState() {
        val names = connectedClients.map { it.name }
        val listeners = connectedClients.map {
            ConnectedListener(
                id = it.id,
                name = it.name,
                ip = it.socket.inetAddress.hostAddress ?: ""
            )
        }
        val count = connectedClients.size
        _uiState.update {
            it.copy(
                connectionState = if (count > 0) DualSyncConnectionState.CONNECTED else DualSyncConnectionState.ADVERTISING,
                connectedListeners = listeners,
                connectedDeviceNames = names,
                connectedDeviceName = names.firstOrNull(),
                connectedDeviceCount = count,
                isSyncActive = count > 0,
                statusMessage = when {
                    count >= MAX_LISTENERS -> "Party is full (6/6 listeners connected)"
                    count > 0 -> "Party Active • $count / $MAX_LISTENERS listeners connected"
                    else -> "Party Ready • 0 / $MAX_LISTENERS listeners connected"
                }
            )
        }
    }

    private fun broadcastListenerCount() {
        val count = connectedClients.size
        val packet = JSONObject().apply {
            put("action", CMD_LISTENER_COUNT_UPDATE)
            put("count", count)
            put("max", MAX_LISTENERS)
        }
        broadcastToListeners(packet)
    }

    private fun startHostSyncHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isPartyActive && isActive) {
                delay(3500)
                if (connectedClients.isNotEmpty()) {
                    val state = playerManager.state.value
                    val packet = JSONObject().apply {
                        put("action", CMD_SYNC_HEARTBEAT)
                        put("pos", state.currentPositionMs)
                        put("isPlaying", state.isPlaying)
                        put("ts", System.currentTimeMillis())
                    }
                    broadcastToListeners(packet)
                }
            }
        }
    }

    // =========================================================================
    // LISTENER FLOW: JOIN PARTY & RECEIVE STREAM
    // =========================================================================

    /**
     * Joins an active Host party using decoded QR code content.
     */
    fun joinPartyFromQr(qrContent: String) {
        disconnect()

        val parsed = parseQrPayload(qrContent)
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    connectionState = DualSyncConnectionState.ERROR,
                    errorMessage = "Invalid QR Code. Please scan the QR code from Host's Dual Listen screen."
                )
            }
            return
        }

        val (hostIp, hostPort, httpPort, sessionId, token, hostPartyName) = parsed

        _uiState.update {
            it.copy(
                role = DualSyncRole.CLIENT,
                connectionState = DualSyncConnectionState.CONNECTING,
                partyName = hostPartyName,
                hostIp = hostIp,
                hostPort = hostPort,
                httpPort = httpPort,
                sessionId = sessionId,
                sessionToken = token,
                errorMessage = null,
                statusMessage = "Connecting to $hostPartyName..."
            )
        }

        isPartyActive = true

        scope.launch {
            try {
                val socket = Socket(hostIp, hostPort).apply {
                    tcpNoDelay = true
                    soTimeout = 8_000 // 8s handshake timeout
                }
                clientSocket = socket

                val writer = PrintWriter(socket.getOutputStream(), true)
                clientWriter = writer
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Send JOIN handshake
                val joinRequest = JSONObject().apply {
                    put("action", CMD_JOIN)
                    put("session", sessionId)
                    put("token", token)
                    put("name", getUserDeviceName())
                }
                writer.println(joinRequest.toString())

                // Wait for Host response
                val responseLine = reader.readLine()
                if (responseLine.isNullOrBlank()) {
                    throw Exception("Host did not respond to join request.")
                }

                val responseJson = JSONObject(responseLine)
                val action = responseJson.optString("action")

                if (action == CMD_REJECT) {
                    val message = responseJson.optString("message", "Connection was rejected by Host.")
                    _uiState.update {
                        it.copy(
                            connectionState = DualSyncConnectionState.ERROR,
                            role = DualSyncRole.NONE,
                            errorMessage = message,
                            statusMessage = message
                        )
                    }
                    socket.close()
                    return@launch
                }

                if (action != CMD_JOIN_OK) {
                    throw Exception("Unexpected response from Host: $action")
                }

                // Successful connection!
                socket.soTimeout = 0 // Persistent connection
                val partyTitle = responseJson.optString("partyName", hostPartyName)
                val count = responseJson.optInt("listenerCount", 1)
                val max = responseJson.optInt("maxListeners", MAX_LISTENERS)

                _uiState.update {
                    it.copy(
                        connectionState = DualSyncConnectionState.CONNECTED,
                        role = DualSyncRole.CLIENT,
                        partyName = partyTitle,
                        connectedDeviceName = partyTitle,
                        connectedDeviceCount = count,
                        maxDevices = max,
                        isSyncActive = true,
                        errorMessage = null,
                        statusMessage = "Connected • Synced with $partyTitle"
                    )
                }

                // Check if Host has a track currently playing (Late Joining!)
                val trackObj = responseJson.optJSONObject("currentTrack")
                if (trackObj != null) {
                    handleTrackStreamFromHost(trackObj)
                }

                // Start Listener Command Receiver Loop
                startListenerMessageLoop(reader)
            } catch (e: Exception) {
                Log.e(TAG, "Client join error: ${e.message}")
                _uiState.update {
                    it.copy(
                        connectionState = DualSyncConnectionState.ERROR,
                        role = DualSyncRole.NONE,
                        errorMessage = if (e is SocketTimeoutException)
                            "Could not reach Host. Ensure you are connected to Host's Wi-Fi Hotspot."
                        else
                            "Connection failed: ${e.localizedMessage ?: "Unknown error"}",
                        statusMessage = "Could not connect to party"
                    )
                }
                disconnect()
            }
        }
    }

    private fun startListenerMessageLoop(reader: BufferedReader) {
        clientReaderJob?.cancel()
        clientReaderJob = scope.launch {
            try {
                while (isPartyActive && isActive) {
                    val line = reader.readLine() ?: break
                    handleIncomingHostMessage(line)
                }
            } catch (_: Exception) {
                // Disconnected from Host
            } finally {
                if (isPartyActive && _uiState.value.role == DualSyncRole.CLIENT) {
                    _uiState.update {
                        it.copy(
                            connectionState = DualSyncConnectionState.DISCONNECTED,
                            role = DualSyncRole.NONE,
                            errorMessage = "Disconnected from party.",
                            statusMessage = "Party ended or connection dropped."
                        )
                    }
                    scope.launch(Dispatchers.Main) {
                        playerManager.stop()
                    }
                }
                disconnect()
            }
        }
    }

    private fun handleIncomingHostMessage(line: String) {
        try {
            val json = JSONObject(line)
            when (json.optString("action")) {
                CMD_TRACK_CHANGE -> {
                    handleTrackStreamFromHost(json)
                }
                CMD_PLAY -> {
                    val pos = json.optLong("pos", 0L)
                    val ts = json.optLong("ts", System.currentTimeMillis())
                    val latency = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
                    val targetPos = pos + latency

                    isInternalCommandExecuting = true
                    scope.launch(Dispatchers.Main) {
                        try {
                            if (targetPos > 0) playerManager.seekTo(targetPos)
                            playerManager.play()
                            _uiState.update { it.copy(isHostMusicPlaying = true) }
                        } finally {
                            isInternalCommandExecuting = false
                        }
                    }
                }
                CMD_PAUSE -> {
                    val pos = json.optLong("pos", -1L)
                    isInternalCommandExecuting = true
                    scope.launch(Dispatchers.Main) {
                        try {
                            if (pos >= 0) playerManager.seekTo(pos)
                            playerManager.pause()
                            _uiState.update { it.copy(isHostMusicPlaying = false) }
                        } finally {
                            isInternalCommandExecuting = false
                        }
                    }
                }
                CMD_STOP -> {
                    isInternalCommandExecuting = true
                    scope.launch(Dispatchers.Main) {
                        try {
                            playerManager.stop()
                            _uiState.update { it.copy(isHostMusicPlaying = false) }
                        } finally {
                            isInternalCommandExecuting = false
                        }
                    }
                }
                CMD_SEEK -> {
                    val pos = json.optLong("pos", 0L)
                    isInternalCommandExecuting = true
                    scope.launch(Dispatchers.Main) {
                        try {
                            playerManager.seekTo(pos)
                        } finally {
                            isInternalCommandExecuting = false
                        }
                    }
                }
                CMD_SYNC_HEARTBEAT -> {
                    val pos = json.optLong("pos", 0L)
                    val isPlaying = json.optBoolean("isPlaying", false)
                    val ts = json.optLong("ts", System.currentTimeMillis())
                    val latency = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
                    val expectedPos = pos + latency

                    scope.launch(Dispatchers.Main) {
                        val currentLocalPos = playerManager.state.value.currentPositionMs
                        val drift = Math.abs(currentLocalPos - expectedPos)
                        // If drift exceeds 800ms, gently resync position
                        if (drift > 800L && isPlaying) {
                            isInternalCommandExecuting = true
                            try {
                                playerManager.seekTo(expectedPos)
                            } finally {
                                isInternalCommandExecuting = false
                            }
                        }
                        _uiState.update { it.copy(isHostMusicPlaying = isPlaying) }
                    }
                }
                CMD_LISTENER_COUNT_UPDATE -> {
                    val count = json.optInt("count", 1)
                    val max = json.optInt("max", MAX_LISTENERS)
                    _uiState.update {
                        it.copy(
                            connectedDeviceCount = count,
                            maxDevices = max
                        )
                    }
                }
                CMD_PARTY_ENDED -> {
                    _uiState.update {
                        it.copy(
                            connectionState = DualSyncConnectionState.DISCONNECTED,
                            role = DualSyncRole.NONE,
                            statusMessage = "Party was ended by Host.",
                            errorMessage = "Party has ended."
                        )
                    }
                    scope.launch(Dispatchers.Main) {
                        playerManager.stop()
                    }
                    disconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming host message: ${e.message}")
        }
    }

    private fun handleTrackStreamFromHost(trackJson: JSONObject) {
        val title = trackJson.optString("title", "Live Party Stream")
        val artist = trackJson.optString("artist", "Host Stream")
        val duration = trackJson.optLong("duration", 0L)
        val streamUrl = trackJson.optString("streamUrl", "")
        val pos = trackJson.optLong("pos", 0L)
        val isPlaying = trackJson.optBoolean("isPlaying", true)
        val ts = trackJson.optLong("ts", System.currentTimeMillis())

        if (streamUrl.isBlank()) return

        val latency = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
        val startPos = pos + latency

        _uiState.update {
            it.copy(
                currentStreamingTitle = title,
                currentStreamingArtist = artist,
                isHostMusicPlaying = isPlaying,
                statusMessage = "Streaming \"$title\" from Host"
            )
        }

        isInternalCommandExecuting = true
        scope.launch(Dispatchers.Main) {
            try {
                playerManager.playRemoteStream(
                    streamUrl = streamUrl,
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    startPositionMs = startPos,
                    autoPlay = isPlaying
                )
            } finally {
                isInternalCommandExecuting = false
            }
        }
    }

    private data class ParsedQr(
        val ip: String,
        val port: Int,
        val httpPort: Int,
        val session: String,
        val token: String,
        val name: String
    )

    private fun parseQrPayload(raw: String): ParsedQr? {
        val clean = raw.trim()
        try {
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val json = JSONObject(clean)
                val ip = json.optString("ip")
                val port = json.optInt("port", TCP_CONTROL_PORT)
                val httpPort = json.optInt("httpPort", HTTP_STREAM_PORT)
                val session = json.optString("session")
                val token = json.optString("token")
                val name = json.optString("name", "Host's Party")
                if (ip.isNotBlank() && session.isNotBlank() && token.isNotBlank()) {
                    return ParsedQr(ip, port, httpPort, session, token, name)
                }
            }
        } catch (_: Exception) {}

        // Fallback: URI Scheme tunymusic-dual://join?ip=...&port=...
        try {
            if (clean.startsWith("tunymusic-dual://", ignoreCase = true)) {
                val uri = android.net.Uri.parse(clean)
                val ip = uri.getQueryParameter("ip") ?: ""
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: TCP_CONTROL_PORT
                val httpPort = uri.getQueryParameter("httpPort")?.toIntOrNull() ?: HTTP_STREAM_PORT
                val session = uri.getQueryParameter("session") ?: ""
                val token = uri.getQueryParameter("token") ?: ""
                val name = uri.getQueryParameter("name") ?: "Host's Party"
                if (ip.isNotBlank() && session.isNotBlank()) {
                    return ParsedQr(ip, port, httpPort, session, token, name)
                }
            }
        } catch (_: Exception) {}

        // Fallback: Raw IP format (e.g. 192.168.43.1:8890)
        val extractedIp = NetworkUtils.parseIpFromPayload(clean)
        if (extractedIp != null) {
            return ParsedQr(extractedIp, TCP_CONTROL_PORT, HTTP_STREAM_PORT, "default", "default", "Host's Party")
        }

        return null
    }

    // =========================================================================
    // OUTGOING SYNC EVENTS (HOST -> LISTENERS)
    // =========================================================================

    fun onUserPlay(positionMs: Long) {
        if (isInternalCommandExecuting || !isHost() || !isSyncConnected()) return
        val packet = JSONObject().apply {
            put("action", CMD_PLAY)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        }
        broadcastToListeners(packet)
    }

    fun onUserPause(positionMs: Long) {
        if (isInternalCommandExecuting || !isHost() || !isSyncConnected()) return
        val packet = JSONObject().apply {
            put("action", CMD_PAUSE)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        }
        broadcastToListeners(packet)
    }

    fun onUserStop() {
        if (isInternalCommandExecuting || !isHost() || !isSyncConnected()) return
        val packet = JSONObject().apply {
            put("action", CMD_STOP)
            put("ts", System.currentTimeMillis())
        }
        broadcastToListeners(packet)
    }

    fun onUserSeek(positionMs: Long) {
        if (isInternalCommandExecuting || !isHost() || !isSyncConnected()) return
        val packet = JSONObject().apply {
            put("action", CMD_SEEK)
            put("pos", positionMs)
            put("ts", System.currentTimeMillis())
        }
        broadcastToListeners(packet)
    }

    fun sendTrackChange(track: AudioTrack, positionMs: Long = 0L, isPlaying: Boolean = true) {
        if (isInternalCommandExecuting || !isHost()) return

        // Update active track on local HTTP streaming server
        audioStreamServer.updateTrack(track)

        val hostIp = _uiState.value.hostIp
        val sessionId = _uiState.value.sessionId
        val token = _uiState.value.sessionToken
        val streamUrl = "http://$hostIp:$HTTP_STREAM_PORT/audio?session=$sessionId&token=$token"

        val packet = JSONObject().apply {
            put("action", CMD_TRACK_CHANGE)
            put("title", track.displayTitle)
            put("artist", track.displayArtist)
            put("duration", track.durationMs)
            put("streamUrl", streamUrl)
            put("pos", positionMs)
            put("isPlaying", isPlaying)
            put("ts", System.currentTimeMillis())
        }
        broadcastToListeners(packet)
    }

    private fun broadcastToListeners(json: JSONObject) {
        val payload = json.toString()
        scope.launch {
            val dead = mutableListOf<HostClientSession>()
            for (client in connectedClients) {
                try {
                    client.writer.println(payload)
                } catch (_: Exception) {
                    dead.add(client)
                }
            }
            for (d in dead) {
                connectedClients.remove(d)
                try { d.socket.close() } catch (_: Exception) {}
            }
            if (dead.isNotEmpty()) {
                updateHostListenersState()
            }
        }
    }

    // =========================================================================
    // TEARDOWN & DISCONNECT
    // =========================================================================

    /**
     * Ends the party if Host, or leaves the party if Listener.
     */
    fun endParty() {
        if (isHost()) {
            val endPacket = JSONObject().apply {
                put("action", CMD_PARTY_ENDED)
            }
            broadcastToListeners(endPacket)
        }
        disconnect()
    }

    fun disconnect() {
        isPartyActive = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        clientReaderJob?.cancel()
        clientReaderJob = null

        // Stop Audio Server
        audioStreamServer.stop()

        // Close Host Sockets
        try { hostServerSocket?.close() } catch (_: Exception) {}
        hostServerSocket = null

        for (client in connectedClients) {
            try { client.writer.close() } catch (_: Exception) {}
            try { client.socket.close() } catch (_: Exception) {}
        }
        connectedClients.clear()

        // Close Client Uplink
        try {
            clientWriter?.println(JSONObject().apply { put("action", CMD_LEAVE) }.toString())
        } catch (_: Exception) {}
        try { clientWriter?.close() } catch (_: Exception) {}
        clientWriter = null

        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null

        _uiState.update {
            it.copy(
                connectionState = DualSyncConnectionState.DISCONNECTED,
                role = DualSyncRole.NONE,
                partyName = "",
                sessionId = "",
                sessionToken = "",
                qrCodeBitmap = null,
                qrPayload = "",
                connectedListeners = emptyList(),
                connectedDeviceNames = emptyList(),
                connectedDeviceName = null,
                connectedDeviceCount = 0,
                isSyncActive = false,
                currentStreamingTitle = null,
                currentStreamingArtist = null,
                isHostMusicPlaying = false,
                statusMessage = "Offline Dual Listen ready"
            )
        }
    }
}
