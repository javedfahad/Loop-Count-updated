package com.example.transfer

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.model.AudioTrack
import org.json.JSONObject

/**
 * Clean data model representing the payload encoded in the Share To QR code.
 * Keeps payload minimal and lightweight for fast, offline camera scanning.
 */
@Immutable
data class ShareToPayload(
    val hostIp: String,
    val port: Int,
    val sessionId: String,
    val songTitle: String,
    val artistName: String = "",
    val trackCount: Int = 1,
    val protocol: String = PROTOCOL_ID
) {
    companion object {
        const val PROTOCOL_ID = "LOOP_SHARE_V1"

        /**
         * Serializes the payload into a compact URI string.
         */
        fun toPayloadString(payload: ShareToPayload): String {
            return Uri.Builder()
                .scheme("loopify")
                .authority("share")
                .appendQueryParameter("p", PROTOCOL_ID)
                .appendQueryParameter("h", payload.hostIp)
                .appendQueryParameter("pt", payload.port.toString())
                .appendQueryParameter("s", payload.sessionId)
                .appendQueryParameter("t", payload.songTitle)
                .appendQueryParameter("a", payload.artistName)
                .appendQueryParameter("c", payload.trackCount.toString())
                .build()
                .toString()
        }

        /**
         * Parses and validates a scanned QR code string.
         * Returns null if the QR is not a valid Share To code.
         */
        fun fromScannedString(raw: String): ShareToPayload? {
            if (raw.isBlank()) return null

            // 1. Try URI format: loopify://share?...
            try {
                if (raw.startsWith("loopify://share")) {
                    val uri = Uri.parse(raw)
                    val protocol = uri.getQueryParameter("p") ?: ""
                    val host = uri.getQueryParameter("h") ?: ""
                    val port = uri.getQueryParameter("pt")?.toIntOrNull() ?: 8888
                    val session = uri.getQueryParameter("s") ?: ""
                    val title = uri.getQueryParameter("t") ?: ""
                    val artist = uri.getQueryParameter("a") ?: ""
                    val count = uri.getQueryParameter("c")?.toIntOrNull() ?: 1

                    if (host.isNotBlank() && (protocol == PROTOCOL_ID || protocol.isNotBlank())) {
                        return ShareToPayload(
                            hostIp = host,
                            port = port,
                            sessionId = session,
                            songTitle = title.ifBlank { "Shared Track" },
                            artistName = artist,
                            trackCount = count.coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. Try JSON format: {"protocol":"LOOP_SHARE_V1", ...}
            try {
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    val json = JSONObject(raw)
                    val host = json.optString("host", json.optString("ip", ""))
                    val port = json.optInt("port", 8888)
                    val session = json.optString("session", "")
                    val title = json.optString("title", "Shared Track")
                    val artist = json.optString("artist", "")
                    val count = json.optInt("count", 1)

                    if (host.isNotBlank()) {
                        return ShareToPayload(
                            hostIp = host,
                            port = port,
                            sessionId = session,
                            songTitle = title,
                            artistName = artist,
                            trackCount = count.coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Exception) {}

            return null
        }
    }
}

/**
 * State representing the Host's active Share To session.
 */
@Immutable
data class HostShareSessionState(
    val isHosting: Boolean = false,
    val hostIp: String = "",
    val port: Int = 8888,
    val sessionId: String = "",
    val sharedTracks: List<TransferItem> = emptyList(),
    val connectedListenersCount: Int = 0,
    val statusMessage: String = "Ready to share",
    val isTransmitting: Boolean = false,
    val lastError: String? = null
)
