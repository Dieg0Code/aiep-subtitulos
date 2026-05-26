package cl.aiep.subtitulos.transport

import cl.aiep.subtitulos.audio.AudioFrame

enum class TransportId {
    CloudRelay,
    LocalOnly,
}

enum class TransportAvailability {
    Available,
    Unavailable,
}

sealed interface ConnectionResult {
    data object Connected : ConnectionResult
    data class Failed(val reason: String) : ConnectionResult
}

data class SessionDescriptor(
    val relayUrl: String,
    val sessionId: String,
)

interface AudioTransport {
    val id: TransportId
    val priority: Int

    suspend fun probe(): TransportAvailability
    suspend fun connect(session: SessionDescriptor): ConnectionResult
    suspend fun sendAudio(frame: AudioFrame): Boolean
    suspend fun sendText(json: String): Boolean
    suspend fun close()
}
