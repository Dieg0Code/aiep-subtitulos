package cl.aiep.subtitulos.transport

import cl.aiep.subtitulos.audio.AudioFrame

class LocalOnlyTransport : AudioTransport {
    override val id = TransportId.LocalOnly
    override val priority = 0

    override suspend fun probe(): TransportAvailability = TransportAvailability.Available
    override suspend fun connect(session: SessionDescriptor): ConnectionResult = ConnectionResult.Connected
    override suspend fun sendAudio(frame: AudioFrame): Boolean = true
    override suspend fun sendText(json: String): Boolean = true
    override suspend fun close() = Unit
}
