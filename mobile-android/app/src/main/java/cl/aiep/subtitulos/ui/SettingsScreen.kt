package cl.aiep.subtitulos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.prefs.AppPreferences
import cl.aiep.subtitulos.ui.theme.AiepCream
import cl.aiep.subtitulos.ui.theme.AiepCreamSoft
import cl.aiep.subtitulos.ui.theme.AiepInk
import cl.aiep.subtitulos.ui.theme.AiepMuted
import cl.aiep.subtitulos.ui.theme.AiepNavy
import cl.aiep.subtitulos.ui.theme.AiepSurface
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by AppPreferences.stateFlow(context)
        .collectAsStateWithLifecycle(initialValue = AppPreferences.State())

    var relayUrl by remember { mutableStateOf(TextFieldValue(prefs.relayUrl)) }
    LaunchedEffect(prefs.relayUrl) {
        if (relayUrl.text != prefs.relayUrl) {
            relayUrl = TextFieldValue(prefs.relayUrl, selection = TextRange(prefs.relayUrl.length))
        }
    }
    LaunchedEffect(relayUrl.text) {
        val trimmed = relayUrl.text.trim()
        if (trimmed.isNotEmpty() && trimmed != prefs.relayUrl) {
            AppPreferences.setRelayUrl(context, trimmed)
        }
    }

    Scaffold(containerColor = AiepCream) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = AiepNavy,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            SettingsCard(title = stringResource(R.string.settings_section_recognition)) {
                ModeSegmented(
                    selectedMode = prefs.mode,
                    onModeChange = { mode ->
                        coroutineScope.launch { AppPreferences.setMode(context, mode) }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_mode_helper),
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SettingsCard(title = stringResource(R.string.settings_section_connection)) {
                TextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.relay_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = aiepFieldColors(),
                )
                Text(
                    text = stringResource(R.string.settings_relay_helper),
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            BatterySection()

            SettingsCard(title = stringResource(R.string.settings_section_about)) {
                Text(
                    text = stringResource(R.string.settings_about_body),
                    color = AiepInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_version, "0.2.0"),
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = title)
            content()
        }
    }
}

@Composable
private fun ModeSegmented(
    selectedMode: CaptureMode,
    onModeChange: (CaptureMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = AiepCreamSoft, shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeSeg(
            title = stringResource(R.string.mode_speech),
            caption = stringResource(R.string.mode_speech_caption),
            selected = selectedMode == CaptureMode.Speech,
            onClick = { onModeChange(CaptureMode.Speech) },
            modifier = Modifier.weight(1f),
        )
        ModeSeg(
            title = stringResource(R.string.mode_whisper),
            caption = stringResource(R.string.mode_whisper_caption),
            selected = selectedMode == CaptureMode.Pcm,
            onClick = { onModeChange(CaptureMode.Pcm) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSeg(
    title: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = if (selected) AiepSurface else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) AiepNavy else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
            )
            .clickable(enabled = !selected, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = if (selected) AiepNavy else AiepMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
        Text(
            text = caption,
            color = AiepMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BatterySection() {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var ignored by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    SettingsCard(title = stringResource(R.string.settings_section_battery)) {
        Text(
            text = if (ignored) stringResource(R.string.settings_battery_off)
            else stringResource(R.string.settings_battery_on),
            color = AiepInk,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!ignored) {
            Text(
                text = stringResource(R.string.settings_battery_action),
                color = AiepNavy,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(AiepSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, AiepNavy.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
