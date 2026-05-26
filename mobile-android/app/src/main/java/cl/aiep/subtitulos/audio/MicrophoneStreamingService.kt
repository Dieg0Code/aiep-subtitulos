package cl.aiep.subtitulos.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.DEFAULT_RELAY_URL
import cl.aiep.subtitulos.LOG_TAG
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.sessions.ActiveSessionTracker
import cl.aiep.subtitulos.sessions.SessionsRepository
import cl.aiep.subtitulos.transport.CloudRelayTransport
import cl.aiep.subtitulos.transport.ConnectionResult
import cl.aiep.subtitulos.transport.SessionDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MicrophoneStreamingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transport = CloudRelayTransport()
    private var engine: CaptureEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: SessionDescriptor? = null
    private var mode: CaptureMode = CaptureMode.Speech
    private var localSessionId: String? = null
    private var reconnectJob: Job? = null
    private lateinit var repository: SessionsRepository

    override fun onCreate() {
        super.onCreate()
        repository = SessionsRepository.get(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val relayUrl = intent?.getStringExtra(EXTRA_RELAY_URL) ?: DEFAULT_RELAY_URL
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val parsedMode = CaptureMode.fromQueryValue(intent?.getStringExtra(EXTRA_MODE))
        val incomingLocalSessionId = intent?.getStringExtra(EXTRA_LOCAL_SESSION_ID)
        if (sessionId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG, "Cannot start microphone foreground service without RECORD_AUDIO")
            stopSelf()
            return START_NOT_STICKY
        }

        session = SessionDescriptor(relayUrl, sessionId)
        mode = parsedMode
        localSessionId = incomingLocalSessionId
        incomingLocalSessionId?.let { localId ->
            ActiveSessionTracker.setActive(localId)
            scope.launch { repository.updateSessionMode(localId, parsedMode, relaySessionId = sessionId) }
        }
        promoteToForeground("Conectando al relay…")
        acquireWakeLock()
        scheduleReconnect()
        return START_STICKY
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        val finishedLocalId = localSessionId
        if (finishedLocalId != null) {
            ActiveSessionTracker.setActive(null)
            scope.launch { repository.markSessionStopped(finishedLocalId) }
        }
        scope.launch { transport.close() }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startEngine() {
        engine?.stop()
        val current = when (mode) {
            CaptureMode.Pcm -> PcmCaptureEngine(
                context = this,
                scope = scope,
                transport = transport,
                onTransportFailure = { scheduleReconnect() },
            )
            CaptureMode.Speech -> SpeechCaptureEngine(
                context = this,
                scope = scope,
                transport = transport,
                onFinalCaption = buildCaptionSink(),
            )
        }
        engine = current
        current.start()
    }

    private fun buildCaptionSink(): ((String, Long) -> Unit)? {
        val sessionLocalId = localSessionId ?: return null
        return { text, ts ->
            scope.launch {
                runCatching { repository.appendFinalCaption(sessionLocalId, text, ts) }
                    .onFailure { Log.w(LOG_TAG, "appendFinalCaption failed", it) }
            }
        }
    }

    private fun scheduleReconnect() {
        val descriptor = session ?: return
        if (reconnectJob?.isActive == true) return
        scope.launch {
            var attempts = 0
            while (true) {
                when (val result = transport.connect(descriptor)) {
                    ConnectionResult.Connected -> {
                        updateNotification(connectedNotificationText(mode))
                        startEngine()
                        reconnectJob = null
                        return@launch
                    }
                    is ConnectionResult.Failed -> {
                        attempts += 1
                        val waitMs = (1000L * attempts).coerceAtMost(10_000L)
                        Log.w(LOG_TAG, "connect failed: ${result.reason}; retrying in ${waitMs}ms")
                        delay(waitMs)
                    }
                }
            }
        }.also { reconnectJob = it }
    }

    private fun connectedNotificationText(mode: CaptureMode): String = when (mode) {
        CaptureMode.Speech -> "Transcribiendo voz al PC"
        CaptureMode.Pcm -> "Enviando audio al PC"
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Microfono AIEP",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantiene activa la captura de audio para subtítulos en vivo."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_24)
            .setContentTitle("AIEP Subtitulos")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun promoteToForeground(text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(text),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiepSubtitulos:MicStreaming")
            .apply { acquire(2 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "mic_streaming"
        private const val NOTIFICATION_ID = 201
        private const val EXTRA_RELAY_URL = "relayUrl"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_LOCAL_SESSION_ID = "localSessionId"

        fun startIntent(
            context: Context,
            relayUrl: String,
            sessionId: String,
            mode: CaptureMode,
            localSessionId: String?,
        ): Intent =
            Intent(context, MicrophoneStreamingService::class.java).apply {
                putExtra(EXTRA_RELAY_URL, relayUrl)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MODE, mode.queryValue)
                if (localSessionId != null) putExtra(EXTRA_LOCAL_SESSION_ID, localSessionId)
            }
    }
}
