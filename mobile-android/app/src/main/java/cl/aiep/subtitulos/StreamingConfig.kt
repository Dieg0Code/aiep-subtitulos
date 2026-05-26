package cl.aiep.subtitulos

const val DEFAULT_RELAY_URL = "https://aiep-relay-production.up.railway.app"
const val DEFAULT_SAMPLE_RATE = 16_000
const val DEFAULT_CHANNELS = 1
const val LOG_TAG = "AiepSubtitulos"

const val RELAY_ID_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
const val RELAY_ID_LENGTH = 6

enum class CaptureMode(val queryValue: String) {
    Speech("speech"),
    Pcm("pcm");

    companion object {
        fun fromQueryValue(value: String?): CaptureMode =
            when (value?.lowercase()) {
                "pcm" -> Pcm
                else -> Speech
            }
    }
}
