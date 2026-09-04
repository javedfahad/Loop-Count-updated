package com.example.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object NetworkUtils {

    /**
     * Gets the primary local IPv4 address of the device (Wi-Fi or Hotspot).
     */
    fun getLocalIpAddress(context: Context? = null): String {
        try {
            // First check Wi-Fi manager if context available
            if (context != null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    val ip = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            // Iterate through network interfaces (handles portable hotspot & modern Wi-Fi)
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Priority order: wlan0 (Wi-Fi), ap0/softap (Hotspot), p2p (Wi-Fi Direct), eth0
            val priorityInterfaces = listOf("wlan", "ap", "rndis", "p2p", "eth", "tun")

            val sortedInterfaces = interfaces.sortedBy { ni ->
                val index = priorityInterfaces.indexOfFirst { ni.name.contains(it, ignoreCase = true) }
                if (index != -1) index else 99
            }

            for (ni in sortedInterfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val addresses = Collections.list(ni.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        if (!hostAddress.startsWith("127.") && hostAddress != "0.0.0.0") {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "192.168.43.1" // Common Android hotspot default
    }

    /**
     * Returns human-readable device name.
     */
    fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
            else -> String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    fun sanitizeFileName(input: String): String {
        var clean = input.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (clean.isBlank()) clean = "track"
        return clean
    }

    /**
     * Generates a clean QR code bitmap for receiver IP pairing.
     */
    fun generateQrCodeBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
