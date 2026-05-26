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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.export.AiProviderDetector
import cl.aiep.subtitulos.export.AiProviderMode
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
    var aiExpanded by remember { mutableStateOf(false) }
    var aiAdvancedExpanded by remember { mutableStateOf(false) }
    var tokenDraft by remember { mutableStateOf(TextFieldValue("")) }
    var modelDraft by remember { mutableStateOf(TextFieldValue("")) }
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

            SettingsCard(title = "IA experimental") {
                AiTokenSettings(
                    expanded = aiExpanded,
                    advancedExpanded = aiAdvancedExpanded,
                    tokenConfigured = prefs.aiToken.isNotBlank(),
                    providerMode = prefs.aiProviderMode,
                    detectedProvider = AiProviderDetector.detect(prefs.aiToken),
                    modelOverride = prefs.aiModelOverride,
                    tokenDraft = tokenDraft,
                    modelDraft = modelDraft,
                    onExpandedChange = { expanded ->
                        aiExpanded = expanded
                        if (expanded) {
                            tokenDraft = TextFieldValue(
                                prefs.aiToken,
                                selection = TextRange(prefs.aiToken.length),
                            )
                            modelDraft = TextFieldValue(
                                prefs.aiModelOverride,
                                selection = TextRange(prefs.aiModelOverride.length),
                            )
                        }
                    },
                    onAdvancedChange = { aiAdvancedExpanded = it },
                    onTokenChange = { tokenDraft = it },
                    onModelChange = { modelDraft = it },
                    onProviderModeChange = { mode ->
                        coroutineScope.launch { AppPreferences.setAiProviderMode(context, mode) }
                    },
                    onSave = {
                        coroutineScope.launch {
                            AppPreferences.setAiToken(context, tokenDraft.text)
                            AppPreferences.setAiModelOverride(context, modelDraft.text)
                            aiExpanded = false
                            aiAdvancedExpanded = false
                        }
                    },
                    onClear = {
                        coroutineScope.launch {
                            AppPreferences.setAiToken(context, "")
                            AppPreferences.setAiModelOverride(context, "")
                            tokenDraft = TextFieldValue("")
                            modelDraft = TextFieldValue("")
                            aiExpanded = false
                            aiAdvancedExpanded = false
                        }
                    },
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
private fun AiTokenSettings(
    expanded: Boolean,
    advancedExpanded: Boolean,
    tokenConfigured: Boolean,
    providerMode: AiProviderMode,
    detectedProvider: AiProviderMode?,
    modelOverride: String,
    tokenDraft: TextFieldValue,
    modelDraft: TextFieldValue,
    onExpandedChange: (Boolean) -> Unit,
    onAdvancedChange: (Boolean) -> Unit,
    onTokenChange: (TextFieldValue) -> Unit,
    onModelChange: (TextFieldValue) -> Unit,
    onProviderModeChange: (AiProviderMode) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val providerLabel = when {
        providerMode != AiProviderMode.Auto -> "Proveedor: ${providerMode.label}"
        detectedProvider != null -> "Proveedor detectado: ${detectedProvider.label}"
        tokenConfigured -> "Proveedor no detectado"
        else -> "No configurado"
    }
    val status = if (tokenConfigured) "Token guardado · $providerLabel" else providerLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiepCreamSoft, RoundedCornerShape(10.dp))
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Token IA",
                color = AiepInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status,
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = if (expanded) "Ocultar" else "Configurar",
            color = AiepNavy,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }

    if (expanded) {
        TextField(
            value = tokenDraft,
            onValueChange = onTokenChange,
            singleLine = true,
            label = { Text("Token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = aiepFieldColors(),
        )
        Text(
            text = "Se guarda solo en este celular para generar PDF estudiable. Compatible con GitHub Models, OpenAI y Anthropic.",
            color = AiepMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiepCreamSoft, RoundedCornerShape(10.dp))
                .clickable { onAdvancedChange(!advancedExpanded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Opciones avanzadas",
                    color = AiepInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (modelOverride.isBlank()) providerLabel else "$providerLabel · modelo personalizado",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (advancedExpanded) "Ocultar" else "Abrir",
                color = AiepNavy,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (advancedExpanded) {
            ProviderModePicker(
                selected = providerMode,
                onSelected = onProviderModeChange,
            )
            TextField(
                value = modelDraft,
                onValueChange = onModelChange,
                singleLine = true,
                label = { Text("Modelo opcional") },
                modifier = Modifier.fillMaxWidth(),
                colors = aiepFieldColors(),
            )
            Text(
                text = "Dejalo vacio para usar el modelo recomendado del proveedor.",
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = tokenConfigured || tokenDraft.text.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Borrar", color = AiepNavy)
            }
            Button(
                onClick = onSave,
                enabled = tokenDraft.text.trim().isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiepNavy,
                    contentColor = AiepSurface,
                    disabledContainerColor = AiepNavy.copy(alpha = 0.24f),
                    disabledContentColor = AiepSurface.copy(alpha = 0.72f),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Guardar")
            }
        }
    }
}

@Composable
private fun ProviderModePicker(
    selected: AiProviderMode,
    onSelected: (AiProviderMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ProviderChip(
                text = AiProviderMode.Auto.label,
                selected = selected == AiProviderMode.Auto,
                onClick = { onSelected(AiProviderMode.Auto) },
                modifier = Modifier.weight(1f),
            )
            ProviderChip(
                text = "GitHub",
                selected = selected == AiProviderMode.GitHubModels,
                onClick = { onSelected(AiProviderMode.GitHubModels) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ProviderChip(
                text = AiProviderMode.OpenAI.label,
                selected = selected == AiProviderMode.OpenAI,
                onClick = { onSelected(AiProviderMode.OpenAI) },
                modifier = Modifier.weight(1f),
            )
            ProviderChip(
                text = AiProviderMode.Anthropic.label,
                selected = selected == AiProviderMode.Anthropic,
                onClick = { onSelected(AiProviderMode.Anthropic) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProviderChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) AiepNavy else AiepCreamSoft
    val foreground = if (selected) AiepSurface else AiepInk
    Row(
        modifier = modifier
            .height(42.dp)
            .background(background, RoundedCornerShape(10.dp))
            .border(1.dp, AiepNavy.copy(alpha = if (selected) 0f else 0.16f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
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
