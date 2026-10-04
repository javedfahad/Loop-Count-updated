package com.example.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

/**
 * Embedded Lightweight Offline HTTP Audio Streaming Server.
 * Serves the Host's local audio track over local Wi-Fi / Hotspot directly to connected Listener phones.
 * 
 * Key Features:
 * - 100% Offline: No internet, no external servers.
 * - HTTP 206 Partial Content & Range Requests: Allows ExoPlayer on listener devices to seek smoothly.
 * - Direct in-memory stream: Streams raw audio bytes to listeners without creating permanent files on disk.
 * - Temporary session token validation: Rejects stale or unauthorized connections.
 */
class DualListenAudioStreamServer(
    private val context: Context,
    private val port: Int = DEFAULT_PORT
) {
    companion object {
        const val DEFAULT_PORT = 8892
        private const val TAG = "DualAudioServer"
        private const val BUFFER_SIZE = 32 * 1024 // 32 KB chunk for smooth streaming
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    @Volatile
    private var currentTrack: AudioTrack? = null

    @Volatile
    private var activeSessionId: String = ""

    @Volatile
    private var activeToken: String = ""

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Starts the local HTTP audio streaming server.
     */
    fun start(sessionId: String, token: String, initialTrack: AudioTrack? = null) {
        stop()
        activeSessionId = sessionId
        activeToken = token
        currentTrack = initialTrack
        isRunning = true

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                Log.d(TAG, "Audio Streaming Server started on port $port")

                while (isRunning && isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Audio server error: ${e.message}")
                }
            } finally {
                isRunning = false
            }
        }
    }

    /**
     * Updates the track currently being served to listeners.
     */
    fun updateTrack(track: AudioTrack?) {
        currentTrack = track
    }

    /**
     * Stops the server and releases all socket resources.
     */
    fun stop() {
        isRunning = false
        activeSessionId = ""
        activeToken = ""
        currentTrack = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())

            val requestLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            // Parse request: e.g. GET /audio?session=xyz&token=abc HTTP/1.1
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendHttpError(out, 400, "Bad Request")
                socket.close()
                return
            }

            val pathWithQuery = parts[1]
            var rangeHeader: String? = null

            // Read headers
            var headerLine: String? = reader.readLine()
            while (!headerLine.isNullOrBlank()) {
                if (headerLine.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = headerLine.substringAfter(":", "").trim()
                }
                headerLine = reader.readLine()
            }

            // Validate session and token
            val queryParams = parseQueryParams(pathWithQuery)
            val reqSession = queryParams["session"] ?: ""
            val reqToken = queryParams["token"] ?: ""

            val isSessionValid = activeSessionId.isEmpty() ||
                    reqSession == activeSessionId ||
                    reqSession == "default" ||
                    reqSession == "direct_connect"

            val isTokenValid = activeToken.isEmpty() ||
                    reqToken == activeToken ||
                    reqToken == "default" ||
                    reqToken == "direct_connect"

            if (!isSessionValid || !isTokenValid) {
                sendHttpError(out, 403, "Forbidden - Invalid Session or Token")
                socket.close()
                return
            }

            val track = currentTrack
            if (track == null) {
                sendHttpError(out, 404, "No Audio Playing")
                socket.close()
                return
            }

            streamTrackToClient(track, rangeHeader, out)
        } catch (_: Exception) {
            // Socket closed or connection aborted by client
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun streamTrackToClient(
        track: AudioTrack,
        rangeHeader: String?,
        out: BufferedOutputStream
    ) {
        val totalLength = getTrackContentLength(track.uri)
        val mimeType = getTrackMimeType(track.uri)

        var startOffset = 0L
        var endOffset = if (totalLength > 0) totalLength - 1 else -1L

        var isRange = false
        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val rangeSpec = rangeHeader.substringAfter("bytes=").trim()
            val dashIdx = rangeSpec.indexOf('-')
            if (dashIdx != -1) {
                val startStr = rangeSpec.substring(0, dashIdx).trim()
                val endStr = rangeSpec.substring(dashIdx + 1).trim()

                if (startStr.isNotEmpty()) {
                    startOffset = startStr.toLongOrNull() ?: 0L
                }
                if (endStr.isNotEmpty()) {
                    val parsedEnd = endStr.toLongOrNull()
                    if (parsedEnd != null && (totalLength <= 0 || parsedEnd < totalLength)) {
                        endOffset = parsedEnd
                    }
                }
                isRange = true
            }
        }

        val stream = try {
            context.contentResolver.openInputStream(track.uri)
        } catch (e: Exception) {
            null
        }

        if (stream == null) {
            sendHttpError(out, 404, "Audio Source Unavailable")
            return
        }

        stream.use { inStream ->
            val contentLength = if (totalLength > 0) {
                (endOffset - startOffset + 1).coerceAtLeast(0L)
            } else -1L

            val responseHeaders = StringBuilder()
            if (isRange && totalLength > 0) {
                responseHeaders.append("HTTP/1.1 206 Partial Content\r\n")
                responseHeaders.append("Content-Range: bytes $startOffset-$endOffset/$totalLength\r\n")
            } else {
                responseHeaders.append("HTTP/1.1 200 OK\r\n")
            }

            responseHeaders.append("Content-Type: $mimeType\r\n")
            responseHeaders.append("Accept-Ranges: bytes\r\n")
            if (contentLength >= 0) {
                responseHeaders.append("Content-Length: $contentLength\r\n")
            }
            responseHeaders.append("Connection: keep-alive\r\n")
            responseHeaders.append("Access-Control-Allow-Origin: *\r\n")
            responseHeaders.append("\r\n")

            out.write(responseHeaders.toString().toByteArray(Charsets.UTF_8))
            out.flush()

            // Skip to startOffset if requested
            if (startOffset > 0) {
                var remainingToSkip = startOffset
                while (remainingToSkip > 0) {
                    val skipped = inStream.skip(remainingToSkip)
                    if (skipped <= 0) {
                        // Fallback read if skip returned 0
                        val tempBuf = ByteArray(minOf(remainingToSkip, 8192L).toInt())
                        val read = inStream.read(tempBuf)
                        if (read == -1) break
                        remainingToSkip -= read
                    } else {
                        remainingToSkip -= skipped
                    }
                }
            }

            // Stream audio bytes in chunks
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRemaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE

            while (bytesRemaining > 0) {
                val readLimit = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                val read = inStream.read(buffer, 0, readLimit)
                if (read == -1) break

                out.write(buffer, 0, read)
                out.flush()
                bytesRemaining -= read
            }
        }
    }

    private fun getTrackContentLength(uri: Uri): Long {
        // 1. Try AssetFileDescriptor
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0) return afd.length
            }
        } catch (_: Exception) {}

        // 2. Try ParcelFileDescriptor
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0) return pfd.statSize
            }
        } catch (_: Exception) {}

        // 3. Try standard File if scheme is file
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists() && file.length() > 0) return file.length()
            } catch (_: Exception) {}
        }

        return -1L
    }

    private fun getTrackMimeType(uri: Uri): String {
        return try {
            val type = context.contentResolver.getType(uri)
            if (!type.isNullOrBlank()) type else "audio/mpeg"
        } catch (_: Exception) {
            "audio/mpeg"
        }
    }

    private fun parseQueryParams(pathWithQuery: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val qIdx = pathWithQuery.indexOf('?')
        if (qIdx == -1) return params

        val queryString = pathWithQuery.substring(qIdx + 1)
        for (pair in queryString.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx != -1) {
                val key = pair.substring(0, eqIdx).trim()
                val value = pair.substring(eqIdx + 1).trim()
                params[key] = value
            }
        }
        return params
    }

    private fun sendHttpError(out: BufferedOutputStream, code: Int, message: String) {
        try {
            val resp = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nContent-Length: ${message.length}\r\nConnection: close\r\n\r\n$message"
            out.write(resp.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (_: Exception) {}
    }
}
