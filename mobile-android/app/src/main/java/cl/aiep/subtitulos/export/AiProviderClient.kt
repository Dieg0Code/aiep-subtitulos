package cl.aiep.subtitulos.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AiProviderMode(val prefValue: String, val label: String) {
    Auto("auto", "Auto"),
    GitHubModels("github_models", "GitHub Models"),
    OpenAI("openai", "OpenAI"),
    Anthropic("anthropic", "Anthropic");

    companion object {
        fun fromPrefValue(value: String?): AiProviderMode =
            entries.firstOrNull { it.prefValue == value } ?: Auto
    }
}

object AiProviderDetector {
    fun detect(token: String): AiProviderMode? {
        val trimmed = token.trim()
        return when {
            trimmed.startsWith("github_pat_") -> AiProviderMode.GitHubModels
            trimmed.startsWith("ghp_") -> AiProviderMode.GitHubModels
            trimmed.startsWith("gho_") -> AiProviderMode.GitHubModels
            trimmed.startsWith("sk-ant-") -> AiProviderMode.Anthropic
            trimmed.startsWith("sk-proj-") -> AiProviderMode.OpenAI
            trimmed.startsWith("sk-") -> AiProviderMode.OpenAI
            else -> null
        }
    }
}

data class AiProviderConfig(
    val token: String,
    val providerMode: AiProviderMode,
    val modelOverride: String,
) {
    val hasToken: Boolean get() = token.trim().isNotEmpty()

    fun resolvedProvider(): AiProviderMode? =
        when (providerMode) {
            AiProviderMode.Auto -> AiProviderDetector.detect(token)
            else -> providerMode
        }

    fun resolvedModel(provider: AiProviderMode): String =
        modelOverride.trim().ifBlank {
            when (provider) {
                AiProviderMode.GitHubModels -> "openai/gpt-4.1"
                AiProviderMode.OpenAI -> "gpt-4.1"
                AiProviderMode.Anthropic -> "claude-sonnet-4-5"
                AiProviderMode.Auto -> "gpt-4.1"
            }
        }
}

class AiProviderClient(
    private val config: AiProviderConfig,
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun createStudyMarkdown(sessionName: String, transcriptMarkdown: String): String =
        withContext(Dispatchers.IO) {
            val provider = config.resolvedProvider()
                ?: throw IOException("Elige proveedor IA en ajustes avanzados")
            val request = buildRequest(provider, sessionName, transcriptMarkdown)

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("${provider.label} respondio ${response.code}")
                }
                val content = parseStudyMarkdown(provider, raw)
                if (content.isEmpty()) throw IOException("Respuesta IA vacia")
                content
            }
        }

    internal fun buildRequest(
        provider: AiProviderMode,
        sessionName: String,
        transcriptMarkdown: String,
    ): Request {
        val token = config.token.trim()
        val prompt = StudyMaterialPrompt.build(sessionName, transcriptMarkdown)
        val model = config.resolvedModel(provider)
        return when (provider) {
            AiProviderMode.GitHubModels -> chatCompletionsRequest(
                url = GITHUB_MODELS_ENDPOINT,
                token = token,
                model = model,
                prompt = prompt,
                providerHeaders = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2026-03-10",
                ),
            )
            AiProviderMode.OpenAI -> chatCompletionsRequest(
                url = OPENAI_ENDPOINT,
                token = token,
                model = model,
                prompt = prompt,
            )
            AiProviderMode.Anthropic -> anthropicRequest(
                token = token,
                model = model,
                prompt = prompt,
            )
            AiProviderMode.Auto -> error("Provider must be resolved before building request")
        }
    }

    private fun chatCompletionsRequest(
        url: String,
        token: String,
        model: String,
        prompt: String,
        providerHeaders: Map<String, String> = emptyMap(),
    ): Request {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("max_tokens", 4000)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", SYSTEM_PROMPT),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt),
                    ),
            )
            .toString()

        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
        providerHeaders.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun anthropicRequest(token: String, model: String, prompt: String): Request {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("max_tokens", 4000)
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt),
                    ),
            )
            .toString()

        return Request.Builder()
            .url(ANTHROPIC_ENDPOINT)
            .header("x-api-key", token)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
    }

    internal fun parseStudyMarkdown(provider: AiProviderMode, raw: String): String {
        val json = JSONObject(raw)
        return when (provider) {
            AiProviderMode.GitHubModels,
            AiProviderMode.OpenAI -> json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            AiProviderMode.Anthropic -> {
                val content = json.getJSONArray("content")
                buildString {
                    for (index in 0 until content.length()) {
                        val item = content.getJSONObject(index)
                        if (item.optString("type").lowercase(Locale.US) == "text") {
                            append(item.optString("text"))
                        }
                    }
                }.trim()
            }
            AiProviderMode.Auto -> ""
        }
    }

    companion object {
        private const val GITHUB_MODELS_ENDPOINT = "https://models.github.ai/inference/chat/completions"
        private const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val SYSTEM_PROMPT =
            "Eres editor pedagogico para clases tecnicas presenciales. Tu prioridad es fidelidad al docente."
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
