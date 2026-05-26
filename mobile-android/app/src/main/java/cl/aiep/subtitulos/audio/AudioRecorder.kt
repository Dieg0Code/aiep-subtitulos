package cl.aiep.subtitulos.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import cl.aiep.subtitulos.DEFAULT_SAMPLE_RATE
import cl.aiep.subtitulos.LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFrame: (AudioFrame) -> Unit,
) {
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (job != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG, "RECORD_AUDIO permission missing")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            DEFAULT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val frameBytes = DEFAULT_SAMPLE_RATE / 10 * 2
        val bufferSize = maxOf(minBuffer, frameBytes * 4)

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(DEFAULT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        recorder = audioRecord
        job = scope.launch(Dispatchers.IO) {
            var sequence = 0L
            val buffer = ByteArray(frameBytes)
            try {
                audioRecord.startRecording()
                Log.i(LOG_TAG, "AudioRecord started sampleRate=$DEFAULT_SAMPLE_RATE bufferSize=$bufferSize")
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        onFrame(AudioFrame(buffer.copyOf(read), sequence++, System.currentTimeMillis()))
                    } else {
                        Log.w(LOG_TAG, "AudioRecord read returned $read")
                    }
                }
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "AudioRecord failed", error)
            } finally {
                runCatching { audioRecord.stop() }
                audioRecord.release()
                Log.i(LOG_TAG, "AudioRecord stopped")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder = null
    }
}
