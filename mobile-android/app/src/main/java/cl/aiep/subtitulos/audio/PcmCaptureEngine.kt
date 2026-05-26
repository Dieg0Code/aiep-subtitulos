package cl.aiep.subtitulos.audio

import android.content.Context
import android.util.Log
import cl.aiep.subtitulos.LOG_TAG
import cl.aiep.subtitulos.transport.AudioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PcmCaptureEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transport: AudioTransport,
    private val onTransportFailure: () -> Unit,
) : CaptureEngine {
    private var recorder: AudioRecorder? = null

    override fun start() {
        if (recorder != null) return
        CaptionPreviewBus.reset()
        recorder = AudioRecorder(context, scope) { frame ->
            AudioLevelBus.emit((computeRms(frame.pcm) * 3f).coerceIn(0f, 1f))
            scope.launch {
                val sent = transport.sendAudio(frame)
                if (!sent) onTransportFailure()
                if (frame.sequence % 50L == 0L) {
                    Log.i(LOG_TAG, "pcm frame sequence=${frame.sequence} bytes=${frame.pcm.size} sent=$sent")
                }
            }
        }.also { it.start() }
    }

    override fun stop() {
        recorder?.stop()
        recorder = null
        AudioLevelBus.reset()
        CaptionPreviewBus.reset()
    }

    private fun computeRms(pcm: ByteArray): Float {
        if (pcm.size < 2) return 0f
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
            samples += 1
            i += 2
        }
        if (samples == 0) return 0f
        val rms = kotlin.math.sqrt(sumSquares / samples)
        return (rms / 32768.0).toFloat()
    }
}
