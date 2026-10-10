package com.example.sync

import com.example.util.NetworkUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualListenWorstCaseTest {

    @Test
    fun testPrivateIpValidation_worstCases() {
        // Cellular / CGNAT (100.64.0.0/10) MUST be rejected so mobile data never corrupts party sync
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.64.0.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.100.5.2"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.127.255.255"))

        // Link local, loopback, and public WAN IPs MUST be rejected
        assertFalse(NetworkUtils.isPrivateOrLocalIp("127.0.0.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("0.0.0.0"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("169.254.1.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp(null))
        assertFalse(NetworkUtils.isPrivateOrLocalIp(""))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("8.8.8.8"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("1.1.1.1"))

        // Standard private LAN and Hotspot IPs MUST be accepted across all phone vendors
        assertTrue(NetworkUtils.isPrivateOrLocalIp("192.168.43.1"))   // Android default hotspot
        assertTrue(NetworkUtils.isPrivateOrLocalIp("192.168.49.1"))   // Wi-Fi Direct
        assertTrue(NetworkUtils.isPrivateOrLocalIp("192.168.1.100"))  // Home Wi-Fi
        assertTrue(NetworkUtils.isPrivateOrLocalIp("10.0.0.1"))       // Class A private
        assertTrue(NetworkUtils.isPrivateOrLocalIp("172.20.10.1"))    // Class B private / iOS hotspot
        assertTrue(NetworkUtils.isPrivateOrLocalIp("172.31.255.254")) // Class B private upper bound
    }

    @Test
    fun testQrPayloadParsing_allScenarios() {
        // Scenario 1: Standard clean JSON
        val validJson = JSONObject().apply {
            put("app", "TunyMusicDual")
            put("v", 2)
            put("ip", "192.168.43.1")
            put("port", 8890)
            put("httpPort", 8892)
            put("session", "sess123")
            put("token", "tok456")
            put("name", "Outdoor Beats")
        }.toString()

        val parsed1 = parseQrPayloadHelper(validJson)
        assertNotNull(parsed1)
        assertEquals("192.168.43.1", parsed1?.ip)
        assertEquals(8890, parsed1?.port)
        assertEquals(8892, parsed1?.httpPort)
        assertEquals("sess123", parsed1?.session)

        // Scenario 2: Dirty string with surrounding quotes or whitespace
        val dirtyJson = "   \"$validJson\"   \n"
        val parsed2 = parseQrPayloadHelper(dirtyJson)
        assertNotNull(parsed2)
        assertEquals("192.168.43.1", parsed2?.ip)

        // Scenario 3: Embedded inside scanner prefix/suffix text
        val wrappedText = "Scanned QR code: $validJson (end of payload)"
        val parsed3 = parseQrPayloadHelper(wrappedText)
        assertNotNull(parsed3)
        assertEquals("192.168.43.1", parsed3?.ip)

        // Scenario 4: Custom URI scheme
        val uriPayload = "tunymusic-dual://join?ip=192.168.49.1&port=8890&httpPort=8892&session=s1&token=t1&name=ParkParty"
        val parsed4 = parseQrPayloadHelper(uriPayload)
        assertNotNull(parsed4)
        assertEquals("192.168.49.1", parsed4?.ip)
        assertEquals("s1", parsed4?.session)

        // Scenario 5: Raw IP with port (manual connection)
        val rawIpWithPort = "192.168.43.1:9000"
        val parsed5 = parseQrPayloadHelper(rawIpWithPort)
        assertNotNull(parsed5)
        assertEquals("192.168.43.1", parsed5?.ip)
        assertEquals(9000, parsed5?.port)

        // Scenario 6: Pure raw IP without port
        val pureIp = "192.168.43.1"
        val parsed6 = parseQrPayloadHelper(pureIp)
        assertNotNull(parsed6)
        assertEquals("192.168.43.1", parsed6?.ip)
        assertEquals(8890, parsed6?.port)

        // Scenario 7: Garbage non-network string (must return null gracefully)
        val garbage = "https://www.google.com/search?q=music"
        val parsed7 = parseQrPayloadHelper(garbage)
        assertNull(parsed7)
    }

    @Test
    fun testProtocolPacketValidation_handshakeAndHeartbeat() {
        // CMD_JOIN packet
        val joinReq = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_JOIN)
            put("session", "sess_alpha")
            put("token", "tok_beta")
            put("name", "Friend Galaxy S24")
        }
        assertEquals(BluetoothSyncManager.CMD_JOIN, joinReq.optString("action"))
        assertEquals("sess_alpha", joinReq.optString("session"))
        assertEquals("Friend Galaxy S24", joinReq.optString("name"))

        // CMD_JOIN_OK response
        val joinOk = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_JOIN_OK)
            put("partyName", "Outdoor Beats")
            put("listenerCount", 1)
            put("maxListeners", 6)
        }
        assertEquals(BluetoothSyncManager.CMD_JOIN_OK, joinOk.optString("action"))
        assertEquals("Outdoor Beats", joinOk.optString("partyName"))
        assertEquals(1, joinOk.optInt("listenerCount"))
        assertEquals(6, joinOk.optInt("maxListeners"))

        // CMD_PROBE & CMD_PROBE_OK for zero-click auto discovery
        val probe = JSONObject().apply { put("action", BluetoothSyncManager.CMD_PROBE) }
        assertEquals(BluetoothSyncManager.CMD_PROBE, probe.optString("action"))

        val probeOk = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_PROBE_OK)
            put("name", "Host's Party")
            put("ip", "192.168.43.1")
            put("port", BluetoothSyncManager.TCP_CONTROL_PORT)
            put("httpPort", BluetoothSyncManager.HTTP_STREAM_PORT)
            put("session", "hotspot_sess")
            put("token", "hotspot_tok")
        }
        assertEquals(BluetoothSyncManager.CMD_PROBE_OK, probeOk.optString("action"))
        assertEquals("192.168.43.1", probeOk.optString("ip"))
        assertEquals(8890, probeOk.optInt("port"))
        assertEquals(8892, probeOk.optInt("httpPort"))

        // CMD_SYNC_HEARTBEAT synchronization packet
        val heartbeat = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_SYNC_HEARTBEAT)
            put("pos", 45_200L)
            put("isPlaying", true)
            put("ts", 1728500000000L)
        }
        assertEquals(BluetoothSyncManager.CMD_SYNC_HEARTBEAT, heartbeat.optString("action"))
        assertEquals(45_200L, heartbeat.optLong("pos"))
        assertTrue(heartbeat.optBoolean("isPlaying"))
    }

    @Test
    fun testCapacityAndRejectionScenario() {
        val state = DualSyncUiState()
        assertEquals(DualSyncConnectionState.DISCONNECTED, state.connectionState)
        assertEquals(DualSyncRole.NONE, state.role)
        assertEquals(6, state.maxDevices)
        assertEquals(0, state.connectedDeviceCount)

        // Simulate full party (6 listeners connected)
        val fullState = state.copy(
            connectedDeviceCount = 6,
            maxDevices = 6,
            connectionState = DualSyncConnectionState.CONNECTED
        )
        assertTrue(fullState.connectedDeviceCount >= fullState.maxDevices)

        // Verify rejection packet for 7th connecting phone
        val rejectPacket = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_REJECT)
            put("reason", "FULL")
            put("message", "Party is full — maximum 6 listeners.")
        }
        assertEquals(BluetoothSyncManager.CMD_REJECT, rejectPacket.optString("action"))
        assertEquals("FULL", rejectPacket.optString("reason"))
        assertEquals("Party is full — maximum 6 listeners.", rejectPacket.optString("message"))
    }

    @Test
    fun testStreamUrlSanitization_worstCases() {
        val originalStaleUrl = "http://192.168.1.150:8892/audio?session=xyz789&token=abc123"
        val actualConnectedHostIp = "192.168.43.1"

        val sanitized = sanitizeStreamUrlHelper(originalStaleUrl, actualConnectedHostIp, 8892)
        assertEquals("http://192.168.43.1:8892/audio?session=xyz789&token=abc123", sanitized)

        // Empty URL should safely return empty without error
        assertEquals("", sanitizeStreamUrlHelper("", actualConnectedHostIp, 8892))

        // Blank host should return original
        assertEquals(originalStaleUrl, sanitizeStreamUrlHelper(originalStaleUrl, "", 8892))
    }

    @Test
    fun testDirectConnectPayloadGeneration() {
        val party = DiscoveredParty(
            name = "Picnic Party",
            hostIp = "192.168.43.1",
            hostPort = 8890,
            httpPort = 8892,
            sessionId = "sess_picnic",
            sessionToken = "tok_picnic",
            isHotspotGateway = true
        )
        assertTrue(party.isHotspotGateway)
        assertEquals("192.168.43.1", party.hostIp)

        val directPayload = JSONObject().apply {
            put("app", "TunyMusicDual")
            put("v", 2)
            put("ip", party.hostIp)
            put("port", party.hostPort)
            put("httpPort", party.httpPort)
            put("session", party.sessionId)
            put("token", party.sessionToken)
            put("name", party.name)
        }.toString()

        val parsed = parseQrPayloadHelper(directPayload)
        assertNotNull(parsed)
        assertEquals("192.168.43.1", parsed?.ip)
        assertEquals(8890, parsed?.port)
        assertEquals(8892, parsed?.httpPort)
        assertEquals("sess_picnic", parsed?.session)
    }

    // Helpers replicating internal parsing for isolated validation
    private data class ParsedQrResult(val ip: String, val port: Int, val httpPort: Int, val session: String)

    private fun parseQrPayloadHelper(raw: String): ParsedQrResult? {
        val clean = raw.trim().trim('\"', '\'')
        val jsonStart = clean.indexOf('{')
        val jsonEnd = clean.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                val jsonStr = clean.substring(jsonStart, jsonEnd + 1)
                val json = JSONObject(jsonStr)
                val ip = json.optString("ip")
                val port = json.optInt("port", 8890)
                val httpPort = json.optInt("httpPort", 8892)
                val session = json.optString("session")
                if (ip.isNotBlank() && ip != "0.0.0.0" && ip != "null") {
                    return ParsedQrResult(ip, port, httpPort, session)
                }
            } catch (_: Exception) {}
        }

        if (clean.contains("tunymusic-dual://", ignoreCase = true)) {
            try {
                val queryPart = clean.substringAfter("?", "")
                val pairs = queryPart.split("&").associate {
                    val kv = it.split("=")
                    if (kv.size == 2) kv[0] to kv[1] else "" to ""
                }
                val ip = pairs["ip"] ?: ""
                val port = pairs["port"]?.toIntOrNull() ?: 8890
                val httpPort = pairs["httpPort"]?.toIntOrNull() ?: 8892
                val session = pairs["session"] ?: "default"
                if (ip.isNotBlank()) {
                    return ParsedQrResult(ip, port, httpPort, session)
                }
            } catch (_: Exception) {}
        }

        val extractedIp = NetworkUtils.parseIpFromPayload(clean)
        if (extractedIp != null) {
            var port = 8890
            if (clean.contains(":")) {
                val p = clean.substringAfterLast(":").trim().toIntOrNull()
                if (p != null && p in 1024..65535) port = p
            }
            return ParsedQrResult(extractedIp, port, 8892, "default")
        }

        return null
    }

    private fun sanitizeStreamUrlHelper(originalUrl: String, hostIp: String, fallbackHttpPort: Int): String {
        if (originalUrl.isBlank() || hostIp.isBlank()) return originalUrl
        return try {
            val uri = java.net.URI(originalUrl)
            val port = if (uri.port != -1) uri.port else fallbackHttpPort
            val path = if (!uri.path.isNullOrBlank()) uri.path else "/audio"
            val query = if (!uri.query.isNullOrBlank()) "?${uri.query}" else ""
            "http://$hostIp:$port$path$query"
        } catch (_: Exception) {
            originalUrl
        }
    }

    @Test
    fun testDemoAudioGeneration() {
        val tempDir = java.io.File.createTempFile("demo_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()
        val file1 = java.io.File(tempDir, "demo_acoustic_melody.wav")
        val file2 = java.io.File(tempDir, "demo_lofi_focus.wav")

        val start = System.currentTimeMillis()
        val genMethod1 = com.example.data.repository.DemoAudioGenerator::class.java.getDeclaredMethod("generateMelodicAcousticTrack", java.io.File::class.java)
        genMethod1.isAccessible = true
        genMethod1.invoke(com.example.data.repository.DemoAudioGenerator, file1)

        val genMethod2 = com.example.data.repository.DemoAudioGenerator::class.java.getDeclaredMethod("generateLoFiFocusTrack", java.io.File::class.java)
        genMethod2.isAccessible = true
        genMethod2.invoke(com.example.data.repository.DemoAudioGenerator, file2)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(file1.exists() && file1.length() > 1000)
        assertTrue(file2.exists() && file2.length() > 1000)
        println("Generated demo tracks in ${elapsed}ms. File1: ${file1.length()} bytes, File2: ${file2.length()} bytes")
        tempDir.deleteRecursively()
    }
}
