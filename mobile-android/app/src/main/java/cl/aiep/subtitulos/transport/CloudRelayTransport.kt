package cl.aiep.subtitulos.transport

import android.util.Log
import cl.aiep.subtitulos.LOG_TAG
import cl.aiep.subtitulos.audio.AudioFrame
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class CloudRelayTransport : AudioTransport {
    override val id = TransportId.CloudRelay
    override val priority = 2

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private var lastSession: SessionDescriptor? = null
    @Volatile
    private var connected = false

    override suspend fun probe(): TransportAvailability = TransportAvailability.Available

    override suspend fun connect(session: SessionDescriptor): ConnectionResult {
        close()
        lastSession = session
        connected = false
        val result = CompletableDeferred<ConnectionResult>()
        val wsUrl = session.relayUrl
            .trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/guest?s=${session.sessionId}"

        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected = true
                    Log.i(LOG_TAG, "CloudRelay connected session=${session.sessionId}")
                    webSocket.send("""{"kind":"status","text":"android:connected"}""")
                    if (!result.isCompleted) result.complete(ConnectionResult.Connected)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connected = false
                    Log.e(LOG_TAG, "CloudRelay failure", t)
                    if (!result.isCompleted) result.complete(ConnectionResult.Failed(t.message ?: "websocket failure"))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connected = false
                    Log.w(LOG_TAG, "CloudRelay closed code=$code reason=$reason")
                }
            },
        )

        return result.await()
    }

    override suspend fun sendAudio(frame: AudioFrame): Boolean {
        if (!connected) return false
        val ok = socket?.send(frame.pcm.toByteString()) ?: false
        if (!ok) Log.w(LOG_TAG, "CloudRelay send failed sequence=${frame.sequence}")
        return ok
    }

    override suspend fun sendText(json: String): Boolean {
        if (!connected) return false
        val ok = socket?.send(json) ?: false
        if (!ok) Log.w(LOG_TAG, "CloudRelay sendText failed bytes=${json.length}")
        return ok
    }

    override suspend fun close() {
        connected = false
        socket?.close(1000, "client closing")
        socket = null
    }
}
