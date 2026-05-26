package cl.aiep.subtitulos.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.DEFAULT_RELAY_URL
import cl.aiep.subtitulos.export.AiProviderMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore("aiep_prefs")

object AppPreferences {
    private val MODE_KEY = stringPreferencesKey("mode")
    private val RELAY_URL_KEY = stringPreferencesKey("relay_url")
    private val GITHUB_MODELS_TOKEN_KEY = stringPreferencesKey("github_models_token")
    private val AI_TOKEN_KEY = stringPreferencesKey("ai_token")
    private val AI_PROVIDER_MODE_KEY = stringPreferencesKey("ai_provider_mode")
    private val AI_MODEL_OVERRIDE_KEY = stringPreferencesKey("ai_model_override")
    private val BATTERY_DISMISSED_KEY = booleanPreferencesKey("battery_prompt_dismissed")
    private val SESSIONS_INDEX_KEY = stringPreferencesKey("sessions_index")

    data class State(
        val mode: CaptureMode = CaptureMode.Speech,
        val relayUrl: String = DEFAULT_RELAY_URL,
        val aiToken: String = "",
        val aiProviderMode: AiProviderMode = AiProviderMode.Auto,
        val aiModelOverride: String = "",
        val batteryPromptDismissed: Boolean = false,
    )

    fun stateFlow(context: Context): Flow<State> =
        context.prefsStore.data.map { prefs ->
            State(
                mode = CaptureMode.fromQueryValue(prefs[MODE_KEY]),
                relayUrl = prefs[RELAY_URL_KEY] ?: DEFAULT_RELAY_URL,
                aiToken = prefs[AI_TOKEN_KEY] ?: prefs[GITHUB_MODELS_TOKEN_KEY].orEmpty(),
                aiProviderMode = AiProviderMode.fromPrefValue(prefs[AI_PROVIDER_MODE_KEY]),
                aiModelOverride = prefs[AI_MODEL_OVERRIDE_KEY].orEmpty(),
                batteryPromptDismissed = prefs[BATTERY_DISMISSED_KEY] ?: false,
            )
        }

    suspend fun setMode(context: Context, mode: CaptureMode) {
        context.prefsStore.edit { it[MODE_KEY] = mode.queryValue }
    }

    suspend fun setRelayUrl(context: Context, url: String) {
        context.prefsStore.edit { it[RELAY_URL_KEY] = url }
    }

    suspend fun setAiToken(context: Context, token: String) {
        context.prefsStore.edit { prefs ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(AI_TOKEN_KEY)
                prefs.remove(GITHUB_MODELS_TOKEN_KEY)
            } else {
                prefs[AI_TOKEN_KEY] = trimmed
                prefs.remove(GITHUB_MODELS_TOKEN_KEY)
            }
        }
    }

    suspend fun setAiProviderMode(context: Context, mode: AiProviderMode) {
        context.prefsStore.edit { it[AI_PROVIDER_MODE_KEY] = mode.prefValue }
    }

    suspend fun setAiModelOverride(context: Context, model: String) {
        context.prefsStore.edit { prefs ->
            val trimmed = model.trim()
            if (trimmed.isEmpty()) prefs.remove(AI_MODEL_OVERRIDE_KEY)
            else prefs[AI_MODEL_OVERRIDE_KEY] = trimmed
        }
    }

    suspend fun setBatteryPromptDismissed(context: Context) {
        context.prefsStore.edit { it[BATTERY_DISMISSED_KEY] = true }
    }

    fun sessionsIndexFlow(context: Context): Flow<String?> =
        context.prefsStore.data.map { it[SESSIONS_INDEX_KEY] }

    suspend fun setSessionsIndex(context: Context, json: String) {
        context.prefsStore.edit { it[SESSIONS_INDEX_KEY] = json }
    }
}
