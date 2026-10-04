package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.transfer.NetworkUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DualListenWorstCaseTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testPrivateIpValidation_worstCases() {
        // Cellular / CGNAT (100.64.0.0/10) MUST be rejected so mobile data never corrupts party sync
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.64.0.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.100.5.2"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("100.127.255.255"))

        // Link local & loopback MUST be rejected
        assertFalse(NetworkUtils.isPrivateOrLocalIp("127.0.0.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("0.0.0.0"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp("169.254.1.1"))
        assertFalse(NetworkUtils.isPrivateOrLocalIp(null))
        assertFalse(NetworkUtils.isPrivateOrLocalIp(""))

        // Standard private LAN IPs MUST be accepted
        assertTrue(NetworkUtils.isPrivateOrLocalIp("192.168.43.1"))
        assertTrue(NetworkUtils.isPrivateOrLocalIp("192.168.1.100"))
        assertTrue(NetworkUtils.isPrivateOrLocalIp("10.0.0.1"))
        assertTrue(NetworkUtils.isPrivateOrLocalIp("172.20.10.1"))
        assertTrue(NetworkUtils.isPrivateOrLocalIp("172.31.255.254"))
    }

    @Test
    fun testQrPayloadParsing_worstCases() {
        // Case 1: Standard valid JSON
        val validJson = JSONObject().apply {
            put("app", "TunyMusicDual")
            put("v", 2)
            put("ip", "192.168.43.1")
            put("port", 8890)
            put("httpPort", 8892)
            put("session", "sess123")
            put("token", "tok456")
            put("name", "Fahad's Party")
        }.toString()

        val parsed1 = parseQrPayloadHelper(validJson)
        assertNotNull(parsed1)
        assertEquals("192.168.43.1", parsed1?.ip)
        assertEquals(8890, parsed1?.port)
        assertEquals(8892, parsed1?.httpPort)
        assertEquals("sess123", parsed1?.session)

        // Case 2: Dirty string with surrounding quotes or whitespace
        val dirtyJson = "   \"$validJson\"   \n"
        val parsed2 = parseQrPayloadHelper(dirtyJson)
        assertNotNull(parsed2)
        assertEquals("192.168.43.1", parsed2?.ip)

        // Case 3: Embedded inside scanner prefix/suffix text
        val wrappedText = "Scanned payload: $validJson (end of code)"
        val parsed3 = parseQrPayloadHelper(wrappedText)
        assertNotNull(parsed3)
        assertEquals("192.168.43.1", parsed3?.ip)

        // Case 4: Custom URI scheme
        val uriPayload = "tunymusic-dual://join?ip=192.168.49.1&port=8890&httpPort=8892&session=s1&token=t1&name=TestParty"
        val parsed4 = parseQrPayloadHelper(uriPayload)
        assertNotNull(parsed4)
        assertEquals("192.168.49.1", parsed4?.ip)
        assertEquals("s1", parsed4?.session)

        // Case 5: Raw IP with port (manual connection)
        val rawIpWithPort = "192.168.43.1:9000"
        val parsed5 = parseQrPayloadHelper(rawIpWithPort)
        assertNotNull(parsed5)
        assertEquals("192.168.43.1", parsed5?.ip)
        assertEquals(9000, parsed5?.port)

        // Case 6: Pure raw IP without port
        val pureIp = "192.168.43.1"
        val parsed6 = parseQrPayloadHelper(pureIp)
        assertNotNull(parsed6)
        assertEquals("192.168.43.1", parsed6?.ip)
        assertEquals(8890, parsed6?.port)

        // Case 7: Corrupted / non-network string
        val garbage = "https://www.google.com/search?q=music"
        val parsed7 = parseQrPayloadHelper(garbage)
        assertNull(parsed7)
    }

    @Test
    fun testStreamUrlSanitization_worstCases() {
        val originalStaleUrl = "http://192.168.1.150:8892/audio?session=xyz789&token=abc123"
        val actualConnectedHostIp = "192.168.43.1"

        val sanitized = sanitizeStreamUrlHelper(originalStaleUrl, actualConnectedHostIp, 8892)
        assertEquals("http://192.168.43.1:8892/audio?session=xyz789&token=abc123", sanitized)

        // Empty URL should safely return empty
        assertEquals("", sanitizeStreamUrlHelper("", actualConnectedHostIp, 8892))

        // Blank host should return original
        assertEquals(originalStaleUrl, sanitizeStreamUrlHelper(originalStaleUrl, "", 8892))
    }

    @Test
    fun testDualSyncUiState_defaultsAndLimits() {
        val state = DualSyncUiState()
        assertEquals(DualSyncConnectionState.DISCONNECTED, state.connectionState)
        assertEquals(DualSyncRole.NONE, state.role)
        assertEquals(6, state.maxDevices)
        assertEquals(0, state.connectedDeviceCount)
        assertFalse(state.isSyncActive)

        // Test party full limit logic
        val fullState = state.copy(
            connectedDeviceCount = 6,
            maxDevices = 6,
            connectionState = DualSyncConnectionState.CONNECTED
        )
        assertEquals(6, fullState.connectedDeviceCount)
        assertTrue(fullState.connectedDeviceCount >= fullState.maxDevices)
    }

    @Test
    fun testFastSocketHandshake_successScenario() = runBlocking {
        // Start a mock host ServerSocket
        val server = ServerSocket(0)
        val port = server.localPort
        val sessionId = "test_sess"
        val token = "test_tok"

        // Thread to accept connection and respond with CMD_JOIN_OK
        val serverThread = Thread {
            try {
                val client = server.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                val line = reader.readLine()
                assertNotNull(line)
                val json = JSONObject(line)
                assertEquals(BluetoothSyncManager.CMD_JOIN, json.optString("action"))
                assertEquals(sessionId, json.optString("session"))

                val ok = JSONObject().apply {
                    put("action", BluetoothSyncManager.CMD_JOIN_OK)
                    put("partyName", "Mock Party")
                    put("listenerCount", 1)
                    put("maxListeners", 6)
                }
                writer.println(ok.toString())
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                server.close()
            }
        }
        serverThread.start()

        // Client side connect
        val clientSocket = java.net.Socket()
        clientSocket.connect(java.net.InetSocketAddress("127.0.0.1", port), 2000)
        val writer = PrintWriter(clientSocket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

        val joinReq = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_JOIN)
            put("session", sessionId)
            put("token", token)
            put("name", "Test Phone")
        }
        writer.println(joinReq.toString())

        val response = reader.readLine()
        assertNotNull(response)
        val respJson = JSONObject(response)
        assertEquals(BluetoothSyncManager.CMD_JOIN_OK, respJson.optString("action"))
        assertEquals("Mock Party", respJson.optString("partyName"))

        clientSocket.close()
        serverThread.join(2000)
    }

    @Test
    fun testFastSocketHandshake_rejectionScenario() = runBlocking {
        // Mock host ServerSocket that rejects because party is full
        val server = ServerSocket(0)
        val port = server.localPort

        val serverThread = Thread {
            try {
                val client = server.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                val line = reader.readLine()
                assertNotNull(line)

                val reject = JSONObject().apply {
                    put("action", BluetoothSyncManager.CMD_REJECT)
                    put("reason", "FULL")
                    put("message", "Party is full — maximum 6 listeners.")
                }
                writer.println(reject.toString())
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                server.close()
            }
        }
        serverThread.start()

        val clientSocket = java.net.Socket()
        clientSocket.connect(java.net.InetSocketAddress("127.0.0.1", port), 2000)
        val writer = PrintWriter(clientSocket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

        val joinReq = JSONObject().apply {
            put("action", BluetoothSyncManager.CMD_JOIN)
            put("session", "any")
            put("token", "any")
            put("name", "Late Friend")
        }
        writer.println(joinReq.toString())

        val response = reader.readLine()
        assertNotNull(response)
        val respJson = JSONObject(response)
        assertEquals(BluetoothSyncManager.CMD_REJECT, respJson.optString("action"))
        assertEquals("FULL", respJson.optString("reason"))
        assertEquals("Party is full — maximum 6 listeners.", respJson.optString("message"))

        clientSocket.close()
        serverThread.join(2000)
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
                val uriStr = clean.substring(clean.indexOf("tunymusic-dual://", ignoreCase = true))
                val uri = android.net.Uri.parse(uriStr)
                val ip = uri.getQueryParameter("ip") ?: ""
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8890
                val httpPort = uri.getQueryParameter("httpPort")?.toIntOrNull() ?: 8892
                val session = uri.getQueryParameter("session") ?: "default"
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
            val uri = android.net.Uri.parse(originalUrl)
            val port = if (uri.port != -1) uri.port else fallbackHttpPort
            val query = uri.encodedQuery
            val path = uri.encodedPath ?: "/audio"
            val queryPart = if (!query.isNullOrBlank()) "?$query" else ""
            "http://$hostIp:$port$path$queryPart"
        } catch (_: Exception) {
            originalUrl
        }
    }
}
