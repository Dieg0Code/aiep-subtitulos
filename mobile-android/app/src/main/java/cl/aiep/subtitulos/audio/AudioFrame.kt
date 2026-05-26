package cl.aiep.subtitulos.audio

data class AudioFrame(
    val pcm: ByteArray,
    val sequence: Long,
    val timestampMs: Long,
)
