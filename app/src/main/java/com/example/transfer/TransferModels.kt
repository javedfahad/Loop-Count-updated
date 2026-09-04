package com.example.transfer

import androidx.compose.runtime.Immutable

@Immutable
data class TransferItem(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folderName: String,
    val fileName: String,
    val sizeBytes: Long
)

@Immutable
data class TransferProgress(
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentFolder: String = "",
    val currentFileBytes: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val overallBytesTransferred: Long = 0L,
    val overallBytesTotal: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val transferredItems: List<String> = emptyList()
) {
    val currentFileProgress: Float
        get() = if (currentFileTotalBytes > 0) {
            (currentFileBytes.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val overallProgress: Float
        get() = if (overallBytesTotal > 0) {
            (overallBytesTransferred.toFloat() / overallBytesTotal.toFloat()).coerceIn(0f, 1f)
        } else if (totalCount > 0) {
            (currentIndex.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

@Immutable
data class ReceiverState(
    val isServerRunning: Boolean = false,
    val localIp: String = "",
    val port: Int = 8888,
    val deviceName: String = "",
    val statusMessage: String = "Waiting for sender...",
    val isReceiving: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentFolder: String = "",
    val receivedItemsCount: Int = 0,
    val totalItemsCount: Int = 0,
    val currentFileBytes: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val totalBytesReceived: Long = 0L,
    val totalBytesExpected: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isComplete: Boolean = false,
    val receivedFiles: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val currentFileProgress: Float
        get() = if (currentFileTotalBytes > 0) {
            (currentFileBytes.toFloat() / currentFileTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val overallProgress: Float
        get() = if (totalBytesExpected > 0) {
            (totalBytesReceived.toFloat() / totalBytesExpected.toFloat()).coerceIn(0f, 1f)
        } else if (totalItemsCount > 0) {
            (receivedItemsCount.toFloat() / totalItemsCount.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
