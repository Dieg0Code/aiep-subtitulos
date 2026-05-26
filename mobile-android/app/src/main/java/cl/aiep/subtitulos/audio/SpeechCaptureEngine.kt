package cl.aiep.subtitulos.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import cl.aiep.subtitulos.LOG_TAG
import cl.aiep.subtitulos.transport.AudioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class SpeechCaptureEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transport: AudioTransport,
    private val onFinalCaption: ((text: String, timestampMs: Long) -> Unit)? = null,
) : CaptureEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var shouldRun = false
    private var consecutiveHardErrors = 0
    private var lastSentText = ""

    override fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(LOG_TAG, "Speech recognizer not available on device")
            sendStatus("speech:unavailable")
            return
        }
        if (shouldRun) return
        shouldRun = true
        consecutiveHardErrors = 0
        lastSentText = ""
        CaptionPreviewBus.reset()
        mainHandler.post { startSession() }
    }

    override fun stop() {
        shouldRun = false
        mainHandler.post {
            flushPendingFinal()
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            AudioLevelBus.reset()
            CaptionPreviewBus.reset()
        }
    }

    private fun startSession() {
        if (!shouldRun) return
        sendStatus("speech:starting")
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        if (r == null) {
            Log.e(LOG_TAG, "createSpeechRecognizer returned null")
            sendStatus("speech:unavailable")
            shouldRun = false
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        runCatching { r.startListening(buildIntent()) }
            .onFailure {
                Log.e(LOG_TAG, "startListening failed", it)
                scheduleRestart()
            }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            sendStatus("speech:started")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            AudioLevelBus.emit(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            sendStatus("speech:end")
        }

        override fun onError(error: Int) {
            sendStatus("speech:error:${errorName(error)}")
            if (isHardStop(error)) {
                consecutiveHardErrors += 1
                if (consecutiveHardErrors >= 3) {
                    Log.e(LOG_TAG, "Hard stop after $consecutiveHardErrors consecutive errors")
                    shouldRun = false
                    return
                }
            } else {
                consecutiveHardErrors = 0
            }
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            emit(results, isFinal = true)
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            emit(partialResults, isFinal = false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun emit(bundle: Bundle?, isFinal: Boolean) {
        val text = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (text.isEmpty()) return
        val normalized = CaptionProcessor.normalize(text)
        if (normalized.isEmpty()) return
        if (normalized == lastSentText) return
        lastSentText = normalized
        CaptionPreviewBus.emit(normalized, isFinal)
        sendCaption(normalized, isFinal)
        // Persist every visible caption (partial + final). If the recognizer dies
        // mid-phrase without ever delivering onResults, the last partial the user
        // saw is already on disk. mergeLine in the repo dedupes by overlap so this
        // is idempotent — repeated partials of the same phrase grow one .md line.
        onFinalCaption?.invoke(normalized, System.currentTimeMillis())
        if (isFinal) {
            lastSentText = ""
        }
    }

    private fun scheduleRestart() {
        flushPendingFinal()
        if (!shouldRun) return
        mainHandler.postDelayed({
            runCatching { recognizer?.destroy() }
            recognizer = null
            if (shouldRun) startSession()
        }, 300L)
    }

    private fun flushPendingFinal() {
        val pending = lastSentText
        if (pending.isEmpty()) return
        lastSentText = ""
        onFinalCaption?.invoke(pending, System.currentTimeMillis())
    }

    private fun sendCaption(text: String, isFinal: Boolean) {
        val json = JSONObject()
            .put("kind", "caption")
            .put("text", text)
            .put("isFinal", isFinal)
            .toString()
        scope.launch { transport.sendText(json) }
    }

    private fun sendStatus(text: String) {
        val json = JSONObject()
            .put("kind", "status")
            .put("text", text)
            .toString()
        scope.launch { transport.sendText(json) }
    }

    private fun isHardStop(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
            error == SpeechRecognizer.ERROR_CLIENT

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech-timeout"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network-timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        else -> "unknown-$error"
    }
}
