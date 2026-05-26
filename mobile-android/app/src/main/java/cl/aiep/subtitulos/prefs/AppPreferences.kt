package cl.aiep.subtitulos.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.DEFAULT_RELAY_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore("aiep_prefs")

object AppPreferences {
    private val MODE_KEY = stringPreferencesKey("mode")
    private val RELAY_URL_KEY = stringPreferencesKey("relay_url")
    private val BATTERY_DISMISSED_KEY = booleanPreferencesKey("battery_prompt_dismissed")
    private val SESSIONS_INDEX_KEY = stringPreferencesKey("sessions_index")

    data class State(
        val mode: CaptureMode = CaptureMode.Speech,
        val relayUrl: String = DEFAULT_RELAY_URL,
        val batteryPromptDismissed: Boolean = false,
    )

    fun stateFlow(context: Context): Flow<State> =
        context.prefsStore.data.map { prefs ->
            State(
                mode = CaptureMode.fromQueryValue(prefs[MODE_KEY]),
                relayUrl = prefs[RELAY_URL_KEY] ?: DEFAULT_RELAY_URL,
                batteryPromptDismissed = prefs[BATTERY_DISMISSED_KEY] ?: false,
            )
        }

    suspend fun setMode(context: Context, mode: CaptureMode) {
        context.prefsStore.edit { it[MODE_KEY] = mode.queryValue }
    }

    suspend fun setRelayUrl(context: Context, url: String) {
        context.prefsStore.edit { it[RELAY_URL_KEY] = url }
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
