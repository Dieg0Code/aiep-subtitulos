package cl.aiep.subtitulos.ai

import android.util.Log
import cl.aiep.subtitulos.export.StudyMaterialPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Markdown produced via the ChatGPT subscription, plus possibly-refreshed tokens to persist. */
data class ChatGptResult(val markdown: String, val tokens: ChatGptTokens)

/**
 * Calls OpenAI's internal Codex backend (`chatgpt.com/backend-api/codex/responses`)
 * using a "Sign in with ChatGPT" access token, so usage is billed to the user's
 * subscription. Refreshes the token once on 401 and reports the new tokens back so
 * the caller can persist them.
 */
object ChatGptClient {
    private const val TAG = "AiepSubtitulos"
    private const val ENDPOINT = "https://chatgpt.com/backend-api/codex/responses"
    private const val DEFAULT_MODEL = "gpt-5.2"
    private const val CLIENT_VERSION = "0.50.0"
    private const val USER_AGENT = "codex_cli_rs/$CLIENT_VERSION (Android; AIEP Subtitulos)"
    private const val SYSTEM_PROMPT =
        "Eres editor pedagogico para clases tecnicas presenciales. Tu prioridad es fidelidad al docente."
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun createStudyMarkdown(
        sessionName: String,
        transcriptMarkdown: String,
        tokens: ChatGptTokens,
        model: String = DEFAULT_MODEL,
    ): Result<ChatGptResult> = withContext(Dispatchers.IO) {
        val prompt = StudyMaterialPrompt.build(sessionName, transcriptMarkdown)
        val payload = requestBody(model, prompt)
        Log.i(TAG, "ChatGPT codex request: model=$model accountId=${tokens.accountId.ifBlank { "<none>" }}")

        runCatching {
            var active = tokens
            var text = execute(payload, active)
            if (text == null) {
                // 401 -> refresh once and retry
                active = ChatGptOAuth.refresh(active.refreshToken).getOrThrow()
                text = execute(payload, active)
                    ?: throw IOException("Sesión de ChatGPT expirada. Vuelve a conectar.")
            }
            if (text.isBlank()) throw IOException("Respuesta IA vacía")
            ChatGptResult(text, active)
        }
    }

    /** Returns the accumulated text, or null when the token was rejected (401/403). */
    private fun execute(payload: String, tokens: ChatGptTokens): String? {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .header("chatgpt-account-id", tokens.accountId)
            .header("OpenAI-Beta", "responses=experimental")
            .header("originator", "codex_cli_rs")
            .header("session_id", UUID.randomUUID().toString())
            .header("version", CLIENT_VERSION)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                Log.w(TAG, "ChatGPT codex auth rejected: ${resp.code} ${resp.body?.string()?.take(300)}")
                return null
            }
            if (!resp.isSuccessful) {
                val body = resp.body?.string()?.take(500).orEmpty()
                Log.e(TAG, "ChatGPT codex error ${resp.code}: $body")
                throw IOException("ChatGPT respondió ${resp.code}")
            }
            val source = resp.body?.source() ?: throw IOException("Respuesta IA vacía")
            val out = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (event.optString("type")) {
                    "response.output_text.delta" -> out.append(event.optString("delta"))
                    "response.failed", "error" ->
                        throw IOException("ChatGPT: error en la respuesta")
                }
            }
            return out.toString().trim()
        }
    }

    private fun requestBody(model: String, prompt: String): String =
        JSONObject()
            .put("model", model)
            .put("instructions", SYSTEM_PROMPT)
            .put("stream", true)
            .put("store", false)
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("type", "message")
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "input_text")
                                    .put("text", prompt),
                            ),
                        ),
                ),
            )
            .toString()
}
