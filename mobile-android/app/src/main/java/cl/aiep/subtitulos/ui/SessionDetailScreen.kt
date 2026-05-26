package cl.aiep.subtitulos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.audio.AudioLevelBus
import cl.aiep.subtitulos.audio.CaptionPacer
import cl.aiep.subtitulos.audio.CaptionPacerSnapshot
import cl.aiep.subtitulos.audio.CaptionPreviewBus
import cl.aiep.subtitulos.prefs.AppPreferences
import cl.aiep.subtitulos.qr.QrPayload
import cl.aiep.subtitulos.sessions.ActiveSessionTracker
import cl.aiep.subtitulos.sessions.SessionMeta
import cl.aiep.subtitulos.sessions.SessionsRepository
import cl.aiep.subtitulos.ui.theme.AiepAmber
import cl.aiep.subtitulos.ui.theme.AiepCream
import cl.aiep.subtitulos.ui.theme.AiepCreamSoft
import cl.aiep.subtitulos.ui.theme.AiepInk
import cl.aiep.subtitulos.ui.theme.AiepLine
import cl.aiep.subtitulos.ui.theme.AiepMuted
import cl.aiep.subtitulos.ui.theme.AiepNavy
import cl.aiep.subtitulos.ui.theme.AiepNavyDeep
import cl.aiep.subtitulos.ui.theme.AiepRed
import cl.aiep.subtitulos.ui.theme.AiepSurface
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onStart: (relayUrl: String, sessionId: String, mode: CaptureMode, localSessionId: String) -> Unit,
    onStop: () -> Unit,
    onScanQr: ((QrPayload) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { SessionsRepository.get(context) }
    val sessions by repo.sessions.collectAsStateWithLifecycle()
    val activeId by ActiveSessionTracker.activeId.collectAsState()
    val prefs by AppPreferences.stateFlow(context)
        .collectAsStateWithLifecycle(initialValue = AppPreferences.State())

    val meta = sessions.firstOrNull { it.id == sessionId }
    val otherActive = activeId != null && activeId != sessionId
    val streaming = activeId == sessionId
    val activeOther: SessionMeta? = if (otherActive) sessions.firstOrNull { it.id == activeId } else null

    var sessionCode by remember { mutableStateOf(TextFieldValue("")) }
    var showManualCodeSheet by remember(sessionId) { mutableStateOf(false) }
    var manualCodeDraft by remember(sessionId) { mutableStateOf(TextFieldValue("")) }
    val scrollState = rememberScrollState()

    val canStart = sessionCode.text.trim().length == 6 && !otherActive && meta != null
    val onQrPayload: (QrPayload) -> Unit = { payload ->
        sessionCode = textFieldValueAtEnd(payload.sessionId.uppercase().take(6))
        showManualCodeSheet = false
        coroutineScope.launch {
            repo.updateSessionMode(sessionId, payload.mode, relaySessionId = payload.sessionId)
        }
        if (payload.relayBaseUrl != null && payload.relayBaseUrl != prefs.relayUrl) {
            coroutineScope.launch { AppPreferences.setRelayUrl(context, payload.relayBaseUrl) }
        }
    }

    LaunchedEffect(streaming, sessionCode.text) {
        if (streaming || sessionCode.text.trim().length == 6) {
            showManualCodeSheet = false
        }
    }

    Scaffold(
        containerColor = AiepCream,
        topBar = {
            SessionDetailTopBar(
                meta = meta,
                onBack = onBack,
                onRename = { newName ->
                    coroutineScope.launch { repo.renameSession(sessionId, newName) }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PrimaryActionCard(
                sessionId = sessionCode,
                streaming = streaming,
                onManualClick = {
                    if (!streaming) {
                        manualCodeDraft = textFieldValueAtEnd(sessionCode.text.trim().uppercase())
                        showManualCodeSheet = true
                    }
                },
                onScanRequest = { onScanQr(onQrPayload) },
            )

            if (meta != null) {
                ModeCard(
                    selectedMode = meta.mode,
                    enabled = !streaming,
                    onModeChange = { mode ->
                        coroutineScope.launch { repo.updateSessionMode(sessionId, mode) }
                    },
                )
            }

            if (otherActive && activeOther != null) {
                BlockedByOtherSessionNotice(otherName = activeOther.name)
            }

            StartStopButton(
                streaming = streaming,
                enabled = streaming || canStart,
                onClick = {
                    if (streaming) {
                        onStop()
                    } else if (canStart) {
                        val code = sessionCode.text.trim().uppercase()
                        onStart(prefs.relayUrl, code, meta!!.mode, sessionId)
                    }
                },
            )

            if (streaming) AudioLevelBar()

            HelperText()

            TranscriptPreview(
                meta = meta,
                repo = repo,
                sessionId = sessionId,
                streaming = streaming,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showManualCodeSheet) {
        ManualCodeBottomSheet(
            value = manualCodeDraft,
            onValueChange = { manualCodeDraft = normalizedSessionCodeValue(it) },
            onDismiss = { showManualCodeSheet = false },
            onSave = {
                sessionCode = textFieldValueAtEnd(manualCodeDraft.text.trim().uppercase().take(6))
                showManualCodeSheet = false
            },
        )
    }
}

@Composable
private fun LiveCaptionBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(AiepNavyDeep, RoundedCornerShape(10.dp))
            .border(1.dp, AiepNavy.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = text,
            color = AiepSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun textFieldValueAtEnd(text: String): TextFieldValue =
    TextFieldValue(text, selection = TextRange(text.length))

private fun normalizedSessionCodeValue(value: TextFieldValue): TextFieldValue {
    val normalized = value.text.uppercase().take(6)
    val cursor = value.selection.end.coerceIn(0, normalized.length)
    return TextFieldValue(
        text = normalized,
        selection = TextRange(cursor),
    )
}

@Composable
private fun SessionDetailTopBar(
    meta: SessionMeta?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
) {
    val initialName = meta?.name.orEmpty()
    var title by remember { mutableStateOf(textFieldValueAtEnd(initialName)) }

    LaunchedEffect(initialName) {
        if (initialName.isNotEmpty() && initialName != title.text) {
            title = TextFieldValue(initialName, selection = TextRange(initialName.length))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiepCream)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Text(text = "←", color = AiepNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AiepNavy,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                ),
                cursorBrush = SolidColor(AiepNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Text(
                text = if (meta == null) "Cargando…" else statusLine(meta),
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    LaunchedEffect(title.text) {
        kotlinx.coroutines.delay(600)
        val trimmed = title.text.trim()
        if (trimmed.isNotEmpty() && trimmed != initialName) {
            onRename(trimmed)
        }
    }
}

private fun statusLine(meta: SessionMeta): String {
    val lines = "${meta.captionCount} línea${if (meta.captionCount == 1) "" else "s"}"
    val mode = when (meta.mode) {
        CaptureMode.Speech -> "Speech"
        CaptureMode.Pcm -> "Whisper"
    }
    return "$mode · $lines"
}

@Composable
private fun PrimaryActionCard(
    sessionId: TextFieldValue,
    streaming: Boolean,
    onManualClick: () -> Unit,
    onScanRequest: () -> Unit,
) {
    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel(text = "Conexión")

            ScanQrButton(enabled = !streaming, onClick = onScanRequest)

            ManualCodeSummaryRow(
                code = sessionId.text.trim().uppercase(),
                streaming = streaming,
                onClick = onManualClick,
            )
        }
    }
}

@Composable
private fun ManualCodeSummaryRow(
    code: String,
    streaming: Boolean,
    onClick: () -> Unit,
) {
    val status = when {
        streaming -> "Código bloqueado durante la grabación"
        code.length == 6 -> "Código listo"
        else -> "No configurado"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiepCreamSoft, RoundedCornerShape(10.dp))
            .clickable(enabled = !streaming, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Código manual",
                    color = AiepInk,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (code.length == 6) {
                    Text(
                        text = code,
                        color = AiepNavy,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    )
                }
                Text(
                    text = status,
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = if (code.length == 6) "Editar" else "+",
            color = AiepNavy,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualCodeBottomSheet(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val canSave = value.text.trim().length == 6
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AiepSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Código de sesión",
                    color = AiepNavy,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Ingresa el código que aparece en el PC.",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(stringResource(R.string.manual_code_label)) },
                supportingText = {
                    Text(stringResource(R.string.manual_code_helper), color = AiepMuted)
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 4.sp,
                    color = AiepNavy,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = aiepFieldColors(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Cancelar", color = AiepNavy)
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AiepNavy,
                        contentColor = AiepSurface,
                        disabledContainerColor = AiepNavy.copy(alpha = 0.24f),
                        disabledContentColor = AiepSurface.copy(alpha = 0.72f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Guardar código")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScanQrButton(enabled: Boolean, onClick: () -> Unit) {
    val border = if (enabled) AiepNavy.copy(alpha = 0.45f) else AiepLine
    val background = if (enabled) AiepCream.copy(alpha = 0.55f) else AiepCream.copy(alpha = 0.3f)
    val titleColor = if (enabled) AiepInk else AiepMuted
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(color = background, shape = RoundedCornerShape(14.dp))
            .border(width = 1.5.dp, color = border, shape = RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color = AiepNavy.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_qr_scan_24),
                    contentDescription = null,
                    tint = AiepNavy,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.scan_qr_title),
                    color = titleColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.scan_qr_subtitle),
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    selectedMode: CaptureMode,
    enabled: Boolean,
    onModeChange: (CaptureMode) -> Unit,
) {
    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.mode_title))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = AiepCreamSoft, shape = RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeSegment(
                    title = stringResource(R.string.mode_speech),
                    caption = stringResource(R.string.mode_speech_caption),
                    selected = selectedMode == CaptureMode.Speech,
                    enabled = enabled,
                    onClick = { onModeChange(CaptureMode.Speech) },
                    modifier = Modifier.weight(1f),
                )
                ModeSegment(
                    title = stringResource(R.string.mode_whisper),
                    caption = stringResource(R.string.mode_whisper_caption),
                    selected = selectedMode == CaptureMode.Pcm,
                    enabled = enabled,
                    onClick = { onModeChange(CaptureMode.Pcm) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeSegment(
    title: String,
    caption: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AiepSurface else Color.Transparent
    val border = if (selected) AiepNavy else Color.Transparent
    val alpha = if (enabled) 1f else 0.55f
    Column(
        modifier = modifier
            .background(color = bg, shape = RoundedCornerShape(9.dp))
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(9.dp))
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = (if (selected) AiepNavy else AiepMuted).copy(alpha = alpha),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = caption,
            color = AiepMuted.copy(alpha = alpha),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BlockedByOtherSessionNotice(otherName: String) {
    SurfaceCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiepAmber.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AiepAmber.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "!", color = AiepInk, fontWeight = FontWeight.Black)
            }
            Text(
                text = "La sesión \"$otherName\" está grabando. Deténla primero para usar el micrófono aquí.",
                color = AiepInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StartStopButton(streaming: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val container = if (streaming) AiepNavyDeep else AiepRed
    val disabled = container.copy(alpha = 0.35f)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = AiepSurface,
            disabledContainerColor = disabled,
            disabledContentColor = AiepSurface.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic_24),
                contentDescription = null,
                tint = AiepSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(if (streaming) R.string.action_stop else R.string.action_start),
                color = AiepSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HelperText() {
    Text(
        text = stringResource(R.string.hint_lock_screen),
        color = AiepMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AudioLevelBar() {
    val level by AudioLevelBus.level.collectAsStateWithLifecycle()
    val animated by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(durationMillis = 120),
        label = "audioLevel",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(AiepCreamSoft, RoundedCornerShape(4.dp))
            .border(1.dp, AiepLine, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(AiepNavy, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun TranscriptPreview(
    meta: SessionMeta?,
    repo: SessionsRepository,
    sessionId: String,
    streaming: Boolean,
) {
    if (meta == null) return
    var lines by remember(meta.captionCount) { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var fullText by remember(meta.captionCount, expanded) { mutableStateOf<String?>(null) }
    val liveCaption by CaptionPreviewBus.preview.collectAsStateWithLifecycle()
    val showLiveCaption = streaming && meta.mode == CaptureMode.Speech
    val captionPacer = remember(sessionId) { CaptionPacer(maxVisibleWords = 18) }
    var pacedCaption by remember(sessionId) { mutableStateOf(CaptionPacerSnapshot()) }

    LaunchedEffect(meta.captionCount, sessionId, streaming) {
        if (!streaming) {
            lines = repo.readRecentLines(sessionId, limit = 5)
        }
    }

    LaunchedEffect(expanded, meta.captionCount, sessionId, streaming) {
        if (expanded && !streaming) fullText = repo.readMarkdown(sessionId)
    }

    LaunchedEffect(showLiveCaption) {
        pacedCaption = if (showLiveCaption) {
            captionPacer.set("Escuchando...", instant = true)
        } else {
            captionPacer.reset()
        }
    }

    LaunchedEffect(showLiveCaption, liveCaption.text) {
        if (showLiveCaption && liveCaption.text.isNotBlank()) {
            pacedCaption = captionPacer.set(liveCaption.text)
        }
    }

    LaunchedEffect(showLiveCaption) {
        while (showLiveCaption) {
            delay(300L)
            pacedCaption = captionPacer.tick()
        }
    }

    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(text = "Transcripción")
            if (meta.mode == CaptureMode.Pcm) {
                Text(
                    text = "Esta sesión usa modo Whisper. La transcripción completa está en el PC.",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                return@SurfaceCard
            }
            if (showLiveCaption) {
                LiveCaptionBlock(
                    text = pacedCaption.visibleText.ifBlank { "Escuchando..." },
                )
                return@SurfaceCard
            }
            if (streaming) {
                Text(
                    text = "La transcripción completa está en el PC.",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                return@SurfaceCard
            }
            if (lines.isEmpty()) {
                Text(
                    text = "Aún no hay transcripción. Inicia el micrófono para comenzar.",
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                return@SurfaceCard
            }
            if (!expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { line ->
                        Text(text = line, color = AiepInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Ver transcripción completa",
                    color = AiepNavy,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(top = 4.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(AiepCreamSoft, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        text = fullText.orEmpty(),
                        color = AiepInk,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
                Text(
                    text = "Ocultar",
                    color = AiepNavy,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { expanded = false }
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
