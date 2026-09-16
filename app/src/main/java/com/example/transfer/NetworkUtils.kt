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
     * Attempts to find the Wi-Fi gateway (router or hotspot host IP) using multiple robust techniques.
     */
    fun getGatewayIpAddress(context: Context?): String? {
        if (context == null) return null

        // 1. Try ConnectivityManager link properties (most reliable on Android 6.0+)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNet = cm?.activeNetwork
                val linkProps = cm?.getLinkProperties(activeNet)
                for (route in linkProps?.routes ?: emptyList()) {
                    if (route.isDefaultRoute && route.gateway is Inet4Address) {
                        val gw = route.gateway?.hostAddress
                        if (!gw.isNullOrBlank() && gw != "0.0.0.0") return gw
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Wi-Fi Manager DHCP Info fallback
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            val gatewayInt = dhcp?.gateway ?: 0
            if (gatewayInt != 0) {
                val ip = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    gatewayInt and 0xff,
                    gatewayInt shr 8 and 0xff,
                    gatewayInt shr 16 and 0xff,
                    gatewayInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0") return ip
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Gathers all candidate Hotspot / Wi-Fi IP addresses that could host the receiver.
     */
    fun getHotspotCandidateIps(context: Context?): List<String> {
        val candidates = linkedSetOf<String>()

        // Gateway from active network
        getGatewayIpAddress(context)?.let { candidates.add(it) }

        // Standard Android SoftAP / Hotspot IP addresses
        candidates.add("192.168.43.1")
        candidates.add("192.168.49.1") // Wi-Fi Direct
        candidates.add("192.168.44.1")
        candidates.add("192.168.50.1")
        candidates.add("172.20.10.1") // iOS hotspot default
        candidates.add("10.42.0.1")

        // Subnet expansion based on local IP
        val localIp = getLocalIpAddress(context)
        val prefix = localIp.substringBeforeLast(".", "")
        if (prefix.isNotBlank()) {
            candidates.add("$prefix.1")
            // If local IP is 192.168.43.1 (this device is hotspot), probe connected clients 2..15
            if (localIp == "192.168.43.1" || localIp == "$prefix.1") {
                for (i in 2..15) {
                    candidates.add("$prefix.$i")
                }
            }
        }

        // Check ARP cache for connected clients if available
        try {
            val arpClients = java.io.File("/proc/net/arp")
            if (arpClients.exists()) {
                arpClients.forEachLine { line ->
                    val tokens = line.split(Regex("\\s+"))
                    if (tokens.size >= 4 && tokens[0].matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00" && ip != "0.0.0.0" && ip != localIp) {
                            candidates.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Never include self
        candidates.remove(localIp)
        return candidates.toList()
    }

    /**
     * Opens Android portable hotspot / tethering settings so user can toggle hotspot on/off.
     */
    fun openHotspotSettings(context: Context) {
        val intents = listOf(
            android.content.Intent("android.settings.TETHER_SETTINGS"),
            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens Android Wi-Fi settings so user can connect to receiver's hotspot network.
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
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
        clean = clean.replace(Regex("_+"), "_").trim('_')
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

    /**
     * Decodes a QR code string from a Bitmap if possible.
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            val result = com.google.zxing.qrcode.QRCodeReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts IP from QR payload: "loopify-share://192.168.1.5:8888", "http://192.168.1.5:8888", or raw "192.168.1.5"
     */
    fun parseIpFromPayload(payload: String): String? {
        val clean = payload.trim()
        val regex = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
        val match = regex.find(clean)
        return match?.value
    }
}
