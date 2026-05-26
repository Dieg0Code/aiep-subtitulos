package cl.aiep.subtitulos.qr

import android.net.Uri
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.RELAY_ID_ALPHABET
import cl.aiep.subtitulos.RELAY_ID_LENGTH

object QrParser {
    private val rawSessionRegex = Regex("^[${RELAY_ID_ALPHABET}]{$RELAY_ID_LENGTH}$")

    fun parse(raw: String?): QrPayload? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        parseAsUrl(trimmed)?.let { return it }
        parseAsRawSession(trimmed)?.let { return it }
        return null
    }

    private fun parseAsUrl(raw: String): QrPayload? {
        if (!raw.startsWith("http://", ignoreCase = true) &&
            !raw.startsWith("https://", ignoreCase = true)
        ) return null

        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "http" && scheme != "https") return null

        val sessionRaw = uri.getQueryParameter("s") ?: return null
        val session = sessionRaw.uppercase()
        if (!rawSessionRegex.matches(session)) return null

        val mode = CaptureMode.fromQueryValue(uri.getQueryParameter("mode"))

        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val relayBase = "$scheme://$host$portPart"

        return QrPayload(sessionId = session, mode = mode, relayBaseUrl = relayBase)
    }

    private fun parseAsRawSession(raw: String): QrPayload? {
        val candidate = raw.uppercase()
        if (!rawSessionRegex.matches(candidate)) return null
        return QrPayload(sessionId = candidate, mode = CaptureMode.Speech, relayBaseUrl = null)
    }
}
