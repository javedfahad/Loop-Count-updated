package com.example.util

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
     * Checks if an IP is a valid private LAN address (RFC 1918) suitable for Wi-Fi or Hotspot.
     * Strictly rejects Carrier-Grade NAT (100.64.0.0/10) used by mobile cellular data (LTE/5G).
     */
    fun isPrivateOrLocalIp(ip: String?): Boolean {
        if (ip.isNullOrBlank() || ip == "0.0.0.0" || ip.startsWith("127.")) return false
        // Exclude CGNAT (RFC 6598: 100.64.0.0/10, i.e. 100.64.0.0 - 100.127.255.255)
        if (ip.startsWith("100.")) {
            val parts = ip.split(".")
            val secondOctet = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (secondOctet in 64..127) return false
        }
        // Exclude link-local (169.254.0.0/16) and test networks
        if (ip.startsWith("169.254.") || ip.startsWith("192.0.2.") || ip.startsWith("198.51.100.")) return false

        // Standard RFC 1918 private IPv4 ranges:
        if (ip.startsWith("192.168.")) return true
        if (ip.startsWith("10.")) return true
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            val secondOctet = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (secondOctet in 16..31) return true
        }
        return false
    }

    /**
     * Gets the primary local IPv4 address of the device (Wi-Fi or Hotspot).
     * Strictly avoids cellular (4G/5G/CGNAT) IPs so local network audio sync always works.
     */
    fun getLocalIpAddress(context: Context? = null): String {
        try {
            // Step 1: Active Network LinkProperties via ConnectivityManager
            if (context != null) {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeNet = cm?.activeNetwork
                    if (activeNet != null) {
                        val caps = cm.getNetworkCapabilities(activeNet)
                        val isLocalTransport = caps != null && (
                                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                        )
                        if (isLocalTransport) {
                            val linkProps = cm.getLinkProperties(activeNet)
                            for (la in linkProps?.linkAddresses ?: emptyList()) {
                                val addr = la.address
                                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                    val hostAddr = addr.hostAddress
                                    if (!hostAddr.isNullOrBlank() && isPrivateOrLocalIp(hostAddr)) {
                                        return hostAddr
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Exclude cellular interfaces
            val excludedInterfaces = listOf("rmnet", "ccmni", "pdp", "wwan", "cellular", "dummy", "sit", "ip6tnl", "tun", "v4-rmnet")

            // Prioritize Wi-Fi and Hotspot interfaces
            val priorityInterfaces = listOf("ap", "softap", "swlan", "wlan", "p2p", "rndis", "eth")

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            val candidateInterfaces = interfaces.filter { ni ->
                ni.isUp && !ni.isLoopback && excludedInterfaces.none { ni.name.contains(it, ignoreCase = true) }
            }.sortedBy { ni ->
                val index = priorityInterfaces.indexOfFirst { ni.name.contains(it, ignoreCase = true) }
                if (index != -1) index else 99
            }

            // Phase 2: Check active Hotspot AP interfaces or 192.168.43.1 first
            for (ni in candidateInterfaces) {
                val isHotspotInterface = ni.name.startsWith("ap", ignoreCase = true) ||
                        ni.name.startsWith("softap", ignoreCase = true) ||
                        ni.name.startsWith("swlan", ignoreCase = true)
                for (address in Collections.list(ni.inetAddresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress == "192.168.43.1" || (isHotspotInterface && isPrivateOrLocalIp(hostAddress))) {
                            return hostAddress
                        }
                    }
                }
            }

            // Phase 3: Look for Wi-Fi interface IPv4
            for (ni in candidateInterfaces) {
                val isWifi = ni.name.startsWith("wlan", ignoreCase = true) || ni.name.startsWith("eth", ignoreCase = true)
                if (isWifi) {
                    for (address in Collections.list(ni.inetAddresses)) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val hostAddress = address.hostAddress ?: continue
                            if (isPrivateOrLocalIp(hostAddress)) {
                                return hostAddress
                            }
                        }
                    }
                }
            }

            // Phase 4: Look for any valid private LAN IPv4 address
            for (ni in candidateInterfaces) {
                for (address in Collections.list(ni.inetAddresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        if (isPrivateOrLocalIp(hostAddress)) {
                            return hostAddress
                        }
                    }
                }
            }

            // Phase 5: Check Wi-Fi gateway if available
            val gatewayIp = getGatewayIpAddress(context)
            if (gatewayIp != null && isPrivateOrLocalIp(gatewayIp)) {
                return gatewayIp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "192.168.43.1"
    }

    /**
     * Attempts to find the Wi-Fi gateway (router or hotspot host IP).
     */
    fun getGatewayIpAddress(context: Context?): String? {
        if (context == null) return null

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
     * Generates a clean QR code bitmap for receiver IP pairing.
     */
    fun generateQrCodeBitmap(content: String, size: Int = 600): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
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
     * Gathers all candidate Hotspot / Wi-Fi IP addresses that could host the session.
     */
    fun getHotspotCandidateIps(context: Context?): List<String> {
        val candidates = linkedSetOf<String>()

        // Gateway from active network
        getGatewayIpAddress(context)?.let { candidates.add(it) }

        // Standard Android SoftAP / Hotspot IP addresses
        candidates.add("192.168.43.1")
        candidates.add("192.168.49.1")
        candidates.add("192.168.44.1")
        candidates.add("192.168.50.1")
        candidates.add("172.20.10.1")
        candidates.add("10.42.0.1")

        // Subnet expansion based on local IP
        val localIp = getLocalIpAddress(context)
        val prefix = localIp.substringBeforeLast(".", "")
        if (prefix.isNotBlank()) {
            candidates.add("$prefix.1")
            if (localIp == "192.168.43.1" || localIp == "$prefix.1") {
                for (i in 2..15) {
                    candidates.add("$prefix.$i")
                }
            }
        }

        candidates.remove(localIp)
        return candidates.toList()
    }

    /**
     * Extracts IP from payload: "192.168.1.5:8888", "http://192.168.1.5:8888", or raw "192.168.1.5"
     */
    fun parseIpFromPayload(payload: String): String? {
        val clean = payload.trim()
        val regex = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
        val match = regex.find(clean)
        return match?.value
    }
}
