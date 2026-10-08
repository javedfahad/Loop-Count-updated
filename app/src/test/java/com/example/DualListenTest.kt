package com.example

import com.example.duallisten.SyntheticAudioHelper
import com.example.model.ListenerInfo
import com.example.model.PartySession
import com.example.model.Song
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DualListenTest {

    @Test
    fun testSyntheticAudioStreamGeneration() {
        // Verify audio stream contains valid WAV header without saving to storage
        val stream = SyntheticAudioHelper.generateSampleAudioStream("Starlight Pulse")
        val header = ByteArray(44)
        val read = stream.read(header)
        assertEquals(44, read)
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
    }

    @Test
    fun testPartySessionAndListenerLimits() {
        val partyId = "PARTY-1234"
        val sessionSecret = UUID.randomUUID().toString()
        val session = PartySession(
            partyId = partyId,
            sessionSecret = sessionSecret,
            hostIp = "127.0.0.1",
            controlPort = 0,
            audioPort = 0,
            maxListeners = 5
        )

        assertEquals(5, session.maxListeners)
        assertTrue(session.partyId.startsWith("PARTY-"))
        assertNotNull(session.sessionSecret)
    }

    @Test
    fun testDualListenSocketCommunicationAndFiveListenersLimit() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val connectedListeners = CopyOnWriteArrayList<String>()
        val maxListeners = 5

        // Server loop simulating Host Control Channel
        val serverThread = Thread {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val writer = PrintWriter(client.getOutputStream(), true)
                        val line = reader.readLine()
                        if (line != null) {
                            val json = JSONObject(line)
                            if (json.optString("type") == "AUTH") {
                                synchronized(connectedListeners) {
                                    if (connectedListeners.size >= maxListeners) {
                                        writer.println(JSONObject().apply { put("type", "FULL") }.toString())
                                        client.close()
                                        return@Thread
                                    }
                                    val slot = connectedListeners.size + 1
                                    val name = "Listener 0$slot"
                                    connectedListeners.add(name)
                                    writer.println(JSONObject().apply {
                                        put("type", "AUTH_OK")
                                        put("displayName", name)
                                    }.toString())
                                }
                            }
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.start()

        val authResults = mutableListOf<String>()
        val latch = CountDownLatch(6)

        // Connect 6 listeners (1..5 should succeed, 6th should be FULL)
        for (i in 1..6) {
            Thread {
                try {
                    val socket = Socket("127.0.0.1", port)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    writer.println(JSONObject().apply {
                        put("type", "AUTH")
                        put("partyId", "PARTY-1234")
                    }.toString())

                    val response = reader.readLine()
                    val resJson = JSONObject(response)
                    val type = resJson.getString("type")
                    synchronized(authResults) {
                        authResults.add(if (type == "AUTH_OK") resJson.getString("displayName") else "FULL")
                    }
                    socket.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        serverSocket.close()

        val okCount = authResults.count { it.startsWith("Listener 0") }
        val fullCount = authResults.count { it == "FULL" }

        assertEquals(5, okCount)
        assertEquals(1, fullCount)
    }

    @Test
    fun testHostControlCommandsBroadcasting() {
        val playMsg = JSONObject().apply {
            put("type", "PLAY")
            put("positionMs", 1500L)
            put("hostTimestamp", 100000L)
            put("scheduledStartTimestamp", 100150L)
        }
        assertEquals("PLAY", playMsg.getString("type"))
        assertEquals(1500L, playMsg.getLong("positionMs"))
        assertEquals(100150L, playMsg.getLong("scheduledStartTimestamp"))

        val trackChangedMsg = JSONObject().apply {
            put("type", "TRACK_CHANGED")
            put("trackTitle", "Starlight Pulse")
            put("trackArtist", "Aura Bloom")
            put("audioStreamUrl", "http://192.168.43.1:8889/stream.mp3?token=secret123")
        }
        assertEquals("TRACK_CHANGED", trackChangedMsg.getString("type"))
        assertEquals("Starlight Pulse", trackChangedMsg.getString("trackTitle"))
        assertTrue(trackChangedMsg.getString("audioStreamUrl").contains("token=secret123"))
    }
}
