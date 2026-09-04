package com.example.transfer

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.model.AudioTrack
import kotlinx.coroutines.CancellationException
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WifiTransferManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null

    private val _receiverState = MutableStateFlow(ReceiverState())
    val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    private val _senderProgress = MutableStateFlow<TransferProgress?>(null)
    val senderProgress: StateFlow<TransferProgress?> = _senderProgress.asStateFlow()

    companion object {
        const val DEFAULT_PORT = 8888
        private const val PROTOCOL_HEADER = "LOOPIFY_TRANSFER_V1"
        private const val PROTOCOL_OK = "LOOPIFY_OK"
        private const val PROTOCOL_READY = "READY"
        private const val PROTOCOL_ITEM_OK = "ITEM_OK"
        private const val PROTOCOL_BATCH_COMPLETE = "BATCH_COMPLETE"
        private const val PROTOCOL_ALL_DONE = "ALL_DONE"
    }

    /**
     * Prepares a list of AudioTracks into TransferItems, calculating real file size & names.
     */
    suspend fun prepareTransferItems(tracks: List<AudioTrack>, folderOverrideName: String? = null): List<TransferItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TransferItem>()
        for (track in tracks) {
            var size = 0L
            var displayName = ""

            try {
                context.contentResolver.query(track.uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) {
                            size = cursor.getLong(sizeIdx)
                        }
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            displayName = cursor.getString(nameIdx) ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and use fallback
            }

            if (size <= 0) {
                try {
                    context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
                        size = pfd.statSize
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            if (size <= 0) {
                try {
                    context.contentResolver.openInputStream(track.uri)?.use { stream ->
                        size = stream.available().toLong()
                    }
                } catch (e: Exception) {
                    // Approximate fallback: 128kbps based on duration
                    size = (track.durationMs * 16L).coerceAtLeast(1024L * 1024L)
                }
            }

            if (displayName.isBlank()) {
                val cleanTitle = NetworkUtils.sanitizeFileName(track.title)
                displayName = "$cleanTitle.mp3"
            } else {
                displayName = NetworkUtils.sanitizeFileName(displayName)
                if (!displayName.contains(".")) displayName += ".mp3"
            }

            val targetFolder = folderOverrideName ?: track.folderName.ifBlank { "Loopify" }

            result.add(
                TransferItem(
                    id = track.id,
                    uri = track.uri.toString(),
                    title = track.title,
                    artist = track.displayArtist,
                    durationMs = track.durationMs,
                    folderName = targetFolder,
                    fileName = displayName,
                    sizeBytes = size.coerceAtLeast(1024L)
                )
            )
        }
        result
    }

    /**
     * Starts the high-speed receiver server.
     */
    fun startReceiver(port: Int = DEFAULT_PORT) {
        stopReceiver()
        val localIp = NetworkUtils.getLocalIpAddress(context)
        val deviceName = NetworkUtils.getDeviceModelName()

        _receiverState.value = ReceiverState(
            isServerRunning = true,
            localIp = localIp,
            port = port,
            deviceName = deviceName,
            statusMessage = "Waiting for sender to connect..."
        )

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                while (isActive) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }

                    handleIncomingConnection(client)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _receiverState.update {
                        it.copy(
                            isServerRunning = false,
                            errorMessage = "Server error: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingConnection(socket: Socket) = withContext(Dispatchers.IO) {
        activeClientSocket = socket
        socket.soTimeout = 45000 // 45 sec timeout
        var dataIn: DataInputStream? = null
        var dataOut: DataOutputStream? = null

        try {
            dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))
            dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // Handshake
            val header = dataIn.readUTF()
            if (header != PROTOCOL_HEADER) {
                socket.close()
                return@withContext
            }

            val senderDevice = dataIn.readUTF()
            dataOut.writeUTF("$PROTOCOL_OK ${NetworkUtils.getDeviceModelName()}")
            dataOut.flush()

            val itemCount = dataIn.readInt()
            val totalBatchBytes = dataIn.readLong()

            _receiverState.update {
                it.copy(
                    isReceiving = true,
                    statusMessage = "Receiving $itemCount songs from $senderDevice...",
                    totalItemsCount = itemCount,
                    receivedItemsCount = 0,
                    totalBytesExpected = totalBatchBytes,
                    totalBytesReceived = 0L,
                    receivedFiles = emptyList()
                )
            }

            dataOut.writeUTF(PROTOCOL_READY)
            dataOut.flush()

            val receivedList = mutableListOf<String>()
            val buffer = ByteArray(64 * 1024) // 64KB high speed buffer
            var totalReceived = 0L

            for (index in 0 until itemCount) {
                val title = dataIn.readUTF()
                val artist = dataIn.readUTF()
                val durationMs = dataIn.readLong()
                val folderName = dataIn.readUTF()
                val rawFileName = dataIn.readUTF()
                val fileSize = dataIn.readLong()

                _receiverState.update {
                    it.copy(
                        currentTitle = title,
                        currentArtist = artist,
                        currentFolder = folderName,
                        receivedItemsCount = index,
                        currentFileBytes = 0L,
                        currentFileTotalBytes = fileSize
                    )
                }

                // Resolve target destination file
                val destFile = resolveDestinationFile(folderName, rawFileName)
                val fileOut = FileOutputStream(destFile)

                var fileBytesRead = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastSpeedCalc = 0L

                try {
                    while (fileBytesRead < fileSize) {
                        val toRead = (fileSize - fileBytesRead).coerceAtMost(buffer.size.toLong()).toInt()
                        val bytesRead = dataIn.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        fileOut.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalReceived += bytesRead
                        bytesSinceLastSpeedCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCalcTime
                        var currentSpeed = _receiverState.value.speedBytesPerSec
                        if (elapsed >= 500) {
                            currentSpeed = (bytesSinceLastSpeedCalc * 1000L) / elapsed.coerceAtLeast(1L)
                            lastSpeedCalcTime = now
                            bytesSinceLastSpeedCalc = 0L
                        }

                        _receiverState.update {
                            it.copy(
                                currentFileBytes = fileBytesRead,
                                totalBytesReceived = totalReceived,
                                speedBytesPerSec = currentSpeed
                            )
                        }
                    }
                } finally {
                    fileOut.flush()
                    fileOut.close()
                }

                // Scan into Android MediaStore so it appears immediately in Loopify and Android library
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("audio/*")
                ) { _, _ -> }

                receivedList.add(destFile.name)

                // Confirm item received
                dataOut.writeUTF(PROTOCOL_ITEM_OK)
                dataOut.flush()

                _receiverState.update {
                    it.copy(
                        receivedItemsCount = index + 1,
                        receivedFiles = receivedList.toList()
                    )
                }
            }

            val completeMsg = dataIn.readUTF()
            if (completeMsg == PROTOCOL_BATCH_COMPLETE) {
                dataOut.writeUTF(PROTOCOL_ALL_DONE)
                dataOut.flush()
            }

            _receiverState.update {
                it.copy(
                    isReceiving = false,
                    isComplete = true,
                    statusMessage = "Successfully received $itemCount songs!"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _receiverState.update {
                it.copy(
                    isReceiving = false,
                    errorMessage = "Transfer interrupted: ${e.localizedMessage}"
                )
            }
        } finally {
            try {
                dataIn?.close()
                dataOut?.close()
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun resolveDestinationFile(folderName: String, fileName: String): File {
        // Target: Music/Loopify/{folderName}/
        val cleanFolder = NetworkUtils.sanitizeFileName(folderName.ifBlank { "Loopify" })
        val cleanFile = NetworkUtils.sanitizeFileName(fileName)

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val baseDir = if (musicDir != null && (musicDir.exists() || musicDir.mkdirs())) {
            File(musicDir, "Loopify")
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "Loopify")
        }

        val targetDir = File(baseDir, cleanFolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        var candidate = File(targetDir, cleanFile)
        var counter = 1
        val dotIdx = cleanFile.lastIndexOf('.')
        val namePart = if (dotIdx != -1) cleanFile.substring(0, dotIdx) else cleanFile
        val extPart = if (dotIdx != -1) cleanFile.substring(dotIdx) else ".mp3"

        while (candidate.exists()) {
            candidate = File(targetDir, "${namePart}_$counter$extPart")
            counter++
        }

        return candidate
    }

    /**
     * Connects to receiver and sends all selected items at maximum Wi-Fi throughput.
     */
    fun sendItems(
        receiverIp: String,
        receiverPort: Int = DEFAULT_PORT,
        items: List<TransferItem>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        cancelSending()

        if (items.isEmpty()) {
            onError("No songs selected to send.")
            return
        }

        val totalBytes = items.sumOf { it.sizeBytes }

        _senderProgress.value = TransferProgress(
            currentIndex = 0,
            totalCount = items.size,
            overallBytesTotal = totalBytes,
            currentTitle = "Connecting...",
            transferredItems = emptyList()
        )

        clientJob = scope.launch {
            var socket: Socket? = null
            var dataOut: DataOutputStream? = null
            var dataIn: DataInputStream? = null

            try {
                socket = Socket()
                socket.connect(InetSocketAddress(receiverIp, receiverPort), 10000)
                socket.soTimeout = 45000
                socket.tcpNoDelay = true

                dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // 1. Handshake
                dataOut.writeUTF(PROTOCOL_HEADER)
                dataOut.writeUTF(NetworkUtils.getDeviceModelName())
                dataOut.flush()

                val ack = dataIn.readUTF()
                if (!ack.startsWith(PROTOCOL_OK)) {
                    throw IllegalStateException("Invalid handshake from receiver: $ack")
                }

                // 2. Metadata header
                dataOut.writeInt(items.size)
                dataOut.writeLong(totalBytes)
                dataOut.flush()

                val ready = dataIn.readUTF()
                if (ready != PROTOCOL_READY) {
                    throw IllegalStateException("Receiver not ready")
                }

                val completedFiles = mutableListOf<String>()
                val buffer = ByteArray(64 * 1024) // 64KB chunk
                var overallSent = 0L
                var lastSpeedTime = System.currentTimeMillis()
                var bytesSinceLastSpeed = 0L

                // 3. Send each file
                for ((index, item) in items.withIndex()) {
                    if (!isActive) break

                    _senderProgress.update {
                        it?.copy(
                            currentIndex = index + 1,
                            currentTitle = item.title,
                            currentArtist = item.artist,
                            currentFolder = item.folderName,
                            currentFileBytes = 0L,
                            currentFileTotalBytes = item.sizeBytes
                        )
                    }

                    dataOut.writeUTF(item.title)
                    dataOut.writeUTF(item.artist)
                    dataOut.writeLong(item.durationMs)
                    dataOut.writeUTF(item.folderName)
                    dataOut.writeUTF(item.fileName)
                    dataOut.writeLong(item.sizeBytes)
                    dataOut.flush()

                    var inputStream: InputStream? = null
                    try {
                        inputStream = context.contentResolver.openInputStream(Uri.parse(item.uri))
                    } catch (e: Exception) {
                        // If URI open fails, try as file
                        try {
                            inputStream = File(item.uri).inputStream()
                        } catch (ex: Exception) {
                            // File cannot be opened
                        }
                    }

                    if (inputStream == null) {
                        throw IllegalStateException("Could not read song file: ${item.title}")
                    }

                    var fileBytesSent = 0L
                    BufferedInputStream(inputStream).use { bis ->
                        while (isActive && fileBytesSent < item.sizeBytes) {
                            val toRead = (item.sizeBytes - fileBytesSent).coerceAtMost(buffer.size.toLong()).toInt()
                            val read = bis.read(buffer, 0, toRead)
                            if (read == -1) break

                            dataOut.write(buffer, 0, read)
                            fileBytesSent += read
                            overallSent += read
                            bytesSinceLastSpeed += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSpeedTime
                            var speed = _senderProgress.value?.speedBytesPerSec ?: 0L
                            if (elapsed >= 500) {
                                speed = (bytesSinceLastSpeed * 1000L) / elapsed.coerceAtLeast(1L)
                                lastSpeedTime = now
                                bytesSinceLastSpeed = 0L
                            }

                            _senderProgress.update {
                                it?.copy(
                                    currentFileBytes = fileBytesSent,
                                    overallBytesTransferred = overallSent,
                                    speedBytesPerSec = speed
                                )
                            }
                        }
                    }

                    dataOut.flush()

                    // Wait for item OK
                    val itemOk = dataIn.readUTF()
                    if (itemOk != PROTOCOL_ITEM_OK) {
                        throw IllegalStateException("Receiver failed to store item: ${item.title}")
                    }

                    completedFiles.add(item.fileName)
                    _senderProgress.update {
                        it?.copy(
                            transferredItems = completedFiles.toList()
                        )
                    }
                }

                // Complete
                dataOut.writeUTF(PROTOCOL_BATCH_COMPLETE)
                dataOut.flush()

                val allDone = dataIn.readUTF()
                if (allDone == PROTOCOL_ALL_DONE) {
                    _senderProgress.update {
                        it?.copy(
                            isCompleted = true,
                            currentTitle = "Transfer Complete!",
                            speedBytesPerSec = 0L
                        )
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Canceled by user
                } else {
                    e.printStackTrace()
                    val msg = e.localizedMessage ?: "Connection failed"
                    _senderProgress.update {
                        it?.copy(errorMessage = msg)
                    }
                    withContext(Dispatchers.Main) {
                        onError(msg)
                    }
                }
            } finally {
                try {
                    dataOut?.close()
                    dataIn?.close()
                    socket?.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun stopReceiver() {
        try {
            serverSocket?.close()
            activeClientSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverJob?.cancel()
        serverJob = null
        _receiverState.update { it.copy(isServerRunning = false, isReceiving = false) }
    }

    fun cancelSending() {
        clientJob?.cancel()
        clientJob = null
        _senderProgress.value = null
    }

    fun resetReceiverState() {
        val currentIp = _receiverState.value.localIp.ifBlank { NetworkUtils.getLocalIpAddress(context) }
        _receiverState.value = ReceiverState(
            isServerRunning = _receiverState.value.isServerRunning,
            localIp = currentIp,
            port = _receiverState.value.port,
            deviceName = NetworkUtils.getDeviceModelName(),
            statusMessage = "Waiting for sender..."
        )
    }

    fun resetSenderState() {
        cancelSending()
    }
}
