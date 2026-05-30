package cl.aiep.subtitulos.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * "Sign in with ChatGPT" OAuth (the flow Codex CLL uses) so the user's ChatGPT
 * Plus/Pro subscription can power the AI features. PKCE against auth.openai.com,
 * with a loopback HTTP server catching the browser redirect on localhost.
 *
 * Semi-official: it targets OpenAI's internal Codex backend and can change.
 */
data class ChatGptTokens(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String,
    val expiresAtMs: Long,
)

object ChatGptOAuth {
    private const val TAG = "AiepSubtitulos"
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val ISSUER = "https://auth.openai.com"
    private const val AUTHORIZE = "$ISSUER/oauth/authorize"
    private const val TOKEN = "$ISSUER/oauth/token"
    private const val SCOPE = "openid profile email offline_access"
    private const val ORIGINATOR = "codex_cli_rs"
    private val PORTS = intArrayOf(1455, 1457)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Opens the browser for the user to sign in, waits for the loopback redirect,
     * and exchanges the code for tokens. Blocks (on IO) until the user finishes or
     * the wait times out (~3 min).
     */
    suspend fun login(context: Context): Result<ChatGptTokens> = withContext(Dispatchers.IO) {
        val pkce = Pkce.generate()
        val server = bindLoopback() ?: return@withContext Result.failure(
            IllegalStateException("No se pudo abrir el puerto local (1455/1457)"),
        )
        try {
            val redirectUri = "http://localhost:${server.localPort}/auth/callback"
            val authorizeUrl = buildAuthorizeUrl(pkce, redirectUri)
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            server.soTimeout = 180_000
            val callback = server.accept()
            val params = callback.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                writeBrowserResponse(socket)
                parseQuery(requestLine)
            }

            val returnedState = params["state"]
            val code = params["code"]
            if (returnedState != pkce.state || code.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Respuesta OAuth inválida"))
            }
            exchangeCode(code, redirectUri, pkce.codeVerifier)
        } catch (e: Exception) {
            Log.e(TAG, "ChatGPT OAuth login failed", e)
            Result.failure(e)
        } finally {
            runCatching { server.close() }
        }
    }

    suspend fun refresh(refreshToken: String): Result<ChatGptTokens> = withContext(Dispatchers.IO) {
        runCatching {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .build()
            postToken(form, fallbackRefresh = refreshToken)
        }
    }

    private fun exchangeCode(code: String, redirectUri: String, verifier: String): Result<ChatGptTokens> =
        runCatching {
            val form = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("client_id", CLIENT_ID)
                .add("code_verifier", verifier)
                .build()
            postToken(form, fallbackRefresh = "")
        }

    private fun postToken(form: FormBody, fallbackRefresh: String): ChatGptTokens {
        val request = Request.Builder().url(TOKEN).post(form).build()
        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Token endpoint respondió ${resp.code}")
            val json = JSONObject(raw)
            val access = json.getString("access_token")
            val refresh = json.optString("refresh_token", fallbackRefresh).ifBlank { fallbackRefresh }
            val idToken = json.optString("id_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            return ChatGptTokens(
                accessToken = access,
                refreshToken = refresh,
                accountId = accountIdFromIdToken(idToken),
                expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
            )
        }
    }

    private fun buildAuthorizeUrl(pkce: PkceCodes, redirectUri: String): String =
        Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("originator", ORIGINATOR)
            .build()
            .toString()

    private fun bindLoopback(): ServerSocket? {
        val loopback = InetAddress.getByName("127.0.0.1")
        for (port in PORTS) {
            runCatching { return ServerSocket(port, 1, loopback) }
        }
        return null
    }

    private fun parseQuery(requestLine: String): Map<String, String> {
        // e.g. "GET /auth/callback?code=abc&state=xyz HTTP/1.1"
        val path = requestLine.substringAfter(' ', "").substringBefore(' ')
        val query = path.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun writeBrowserResponse(socket: java.net.Socket) {
        val body = """
            <!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>AIEP Subtítulos</title></head>
            <body style="font-family:system-ui;text-align:center;padding:48px;color:#003B70">
            <h2>✓ Conectado</h2><p>Ya puedes volver a la app AIEP Subtítulos.</p></body></html>
        """.trimIndent()
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
        runCatching {
            socket.getOutputStream().apply {
                write(response.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
    }

    private fun accountIdFromIdToken(idToken: String): String {
        if (idToken.isBlank()) return ""
        return runCatching {
            val payload = idToken.split('.')[1]
            val decoded = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
            JSONObject(decoded)
                .getJSONObject("https://api.openai.com/auth")
                .optString("chatgpt_account_id")
        }.getOrDefault("")
    }
}
