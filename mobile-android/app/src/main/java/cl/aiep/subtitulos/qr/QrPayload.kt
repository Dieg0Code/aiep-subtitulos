package cl.aiep.subtitulos.qr

import cl.aiep.subtitulos.CaptureMode

data class QrPayload(
    val sessionId: String,
    val mode: CaptureMode,
    val relayBaseUrl: String?,
)
