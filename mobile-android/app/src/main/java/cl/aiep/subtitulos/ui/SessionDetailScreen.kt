package cl.aiep.subtitulos.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.R
import cl.aiep.subtitulos.audio.AudioLevelBus
import cl.aiep.subtitulos.audio.CaptionPacer
import cl.aiep.subtitulos.audio.CaptionPacerSnapshot
import cl.aiep.subtitulos.audio.CaptionPreviewBus
import cl.aiep.subtitulos.ai.ChatGptClient
import cl.aiep.subtitulos.export.AiProviderClient
import cl.aiep.subtitulos.export.AiProviderConfig
import cl.aiep.subtitulos.export.AiProviderMode
import cl.aiep.subtitulos.export.PdfShare
import cl.aiep.subtitulos.export.StudyDocxGenerator
import cl.aiep.subtitulos.export.StudyPdfExporter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onStart: (relayUrl: String, sessionId: String, mode: CaptureMode, localSessionId: String, localOnly: Boolean) -> Unit,
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
    var localOnly by remember(sessionId) { mutableStateOf(false) }
    var pdfBusy by remember(sessionId) { mutableStateOf(false) }
    var pdfStatus by remember(sessionId) { mutableStateOf<String?>(null) }
    var showExportSheet by remember(sessionId) { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val canStart = !otherActive && meta != null && (localOnly || sessionCode.text.trim().length == 6)
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

    LaunchedEffect(localOnly, streaming, meta?.mode) {
        if (localOnly && !streaming && meta != null && meta.mode != CaptureMode.Speech) {
            repo.updateSessionMode(sessionId, CaptureMode.Speech)
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
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryActionCard(
                sessionId = sessionCode,
                streaming = streaming,
                localOnly = localOnly,
                onManualClick = {
                    if (!streaming && !localOnly) {
                        manualCodeDraft = textFieldValueAtEnd(sessionCode.text.trim().uppercase())
                        showManualCodeSheet = true
                    }
                },
                onScanRequest = { onScanQr(onQrPayload) },
                onLocalOnlyChange = { enabled ->
                    if (!streaming) {
                        localOnly = enabled
                        if (enabled) showManualCodeSheet = false
                    }
                },
            )

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
                        val code = if (localOnly) "" else sessionCode.text.trim().uppercase()
                        val mode = if (localOnly) CaptureMode.Speech else meta!!.mode
                        onStart(prefs.relayUrl, code, mode, sessionId, localOnly)
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

            if (meta != null && !streaming && meta.mode == CaptureMode.Speech && meta.captionCount > 0) {
                StudyPdfAction(
                    busy = pdfBusy,
                    busyLabel = pdfStatus,
                    onClick = { showExportSheet = true },
                )
            }

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

    if (showExportSheet && meta != null) {
        val sessionName = meta.name
        val chatGptTokens = prefs.chatGptTokens
        val useChatGpt = chatGptTokens != null && (
            prefs.aiProviderMode == AiProviderMode.OpenAiChatGpt ||
                (prefs.aiProviderMode == AiProviderMode.Auto && prefs.aiToken.isBlank())
            )
        ExportOptionsSheet(
            hasAiToken = prefs.aiToken.trim().isNotEmpty() || chatGptTokens != null,
            onDismiss = { showExportSheet = false },
            onDownload = { content, format ->
                showExportSheet = false
                coroutineScope.launch {
                    pdfBusy = true
                    pdfStatus = context.getString(R.string.export_status_preparing)
                    val rawMarkdown = runCatching { repo.readMarkdown(sessionId) }.getOrDefault("")
                    val aiConfig = AiProviderConfig(
                        token = prefs.aiToken.trim(),
                        providerMode = prefs.aiProviderMode,
                        modelOverride = prefs.aiModelOverride,
                    )
                    val studyMarkdown = if (content == ExportContent.Ai) {
                        pdfStatus = context.getString(R.string.export_status_ai)
                        when {
                            useChatGpt && chatGptTokens != null -> runCatching {
                                val result = ChatGptClient
                                    .createStudyMarkdown(sessionName, rawMarkdown, chatGptTokens)
                                    .getOrThrow()
                                if (result.tokens != chatGptTokens) {
                                    AppPreferences.setChatGptTokens(context, result.tokens)
                                }
                                result.markdown
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.export_error_ai),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.getOrNull()

                            aiConfig.hasToken -> runCatching {
                                AiProviderClient(aiConfig).createStudyMarkdown(sessionName, rawMarkdown)
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.export_error_ai),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.getOrNull()

                            else -> null
                        }
                    } else {
                        null
                    }

                    runCatching {
                        when (format) {
                            ExportFormat.Pdf -> {
                                pdfStatus = context.getString(R.string.export_status_pdf)
                                val exporter = StudyPdfExporter(context)
                                val file = if (studyMarkdown.isNullOrBlank()) {
                                    exporter.generateRaw(sessionName, rawMarkdown)
                                } else {
                                    exporter.generate(sessionName, studyMarkdown)
                                }
                                PdfShare.share(context, file, PdfShare.MIME_PDF)
                            }
                            ExportFormat.Docx -> {
                                pdfStatus = context.getString(R.string.export_status_word)
                                val file = withContext(Dispatchers.IO) {
                                    val generator = StudyDocxGenerator(context)
                                    if (studyMarkdown.isNullOrBlank()) {
                                        generator.generateRaw(sessionName, rawMarkdown)
                                    } else {
                                        generator.generate(sessionName, studyMarkdown)
                                    }
                                }
                                PdfShare.share(context, file, PdfShare.MIME_DOCX)
                            }
                        }
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(R.string.export_error_generic),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    pdfBusy = false
                    pdfStatus = null
                }
            },
        )
    }
}

private enum class ExportContent { Ai, Raw }

private enum class ExportFormat { Pdf, Docx }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionsSheet(
    hasAiToken: Boolean,
    onDismiss: () -> Unit,
    onDownload: (ExportContent, ExportFormat) -> Unit,
) {
    var content by remember {
        mutableStateOf(if (hasAiToken) ExportContent.Ai else ExportContent.Raw)
    }
    var format by remember { mutableStateOf(ExportFormat.Pdf) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AiepSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.export_sheet_title),
                    color = AiepNavy,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.export_sheet_subtitle),
                    color = AiepMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportGroupLabel(text = stringResource(R.string.export_content_label))
                ExportOptionCard(
                    icon = R.drawable.ic_sparkles_24,
                    title = stringResource(R.string.export_content_ai),
                    subtitle = stringResource(R.string.export_content_ai_desc),
                    selected = content == ExportContent.Ai,
                    enabled = hasAiToken,
                    accent = AiepAmber,
                    onClick = { content = ExportContent.Ai },
                )
                ExportOptionCard(
                    icon = R.drawable.ic_article_24,
                    title = stringResource(R.string.export_content_raw),
                    subtitle = stringResource(R.string.export_content_raw_desc),
                    selected = content == ExportContent.Raw,
                    enabled = true,
                    onClick = { content = ExportContent.Raw },
                )
                if (!hasAiToken) {
                    Text(
                        text = stringResource(R.string.export_ai_requires_token),
                        color = AiepMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportGroupLabel(text = stringResource(R.string.export_format_label))
                ExportOptionCard(
                    icon = R.drawable.ic_picture_as_pdf_24,
                    title = stringResource(R.string.export_format_pdf),
                    subtitle = stringResource(R.string.export_format_pdf_desc),
                    selected = format == ExportFormat.Pdf,
                    enabled = true,
                    onClick = { format = ExportFormat.Pdf },
                )
                ExportOptionCard(
                    icon = R.drawable.ic_description_24,
                    title = stringResource(R.string.export_format_word),
                    subtitle = stringResource(R.string.export_format_word_desc),
                    selected = format == ExportFormat.Docx,
                    enabled = true,
                    onClick = { format = ExportFormat.Docx },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.action_cancel), color = AiepNavy)
                }
                Button(
                    onClick = { onDownload(content, format) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AiepNavy,
                        contentColor = AiepSurface,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.export_action_download))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExportGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = AiepMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun ExportOptionCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color? = null,
) {
    val borderColor = if (selected && enabled) AiepNavy else AiepLine
    val borderWidth = if (selected && enabled) 1.5.dp else 1.dp
    val background = when {
        !enabled -> AiepCreamSoft.copy(alpha = 0.5f)
        selected -> AiepNavy.copy(alpha = 0.06f)
        else -> AiepSurface
    }
    val titleColor = if (enabled) AiepNavy else AiepMuted
    val iconTint = when {
        !enabled -> AiepMuted.copy(alpha = 0.5f)
        accent != null -> accent
        selected -> AiepNavy
        else -> AiepMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected && enabled) AiepSurface else AiepCreamSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected && enabled) AiepNavy else AiepLine, CircleShape)
                .background(if (selected && enabled) AiepNavy else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (selected && enabled) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AiepSurface),
                )
            }
        }
    }
}

@Composable
private fun StudyPdfAction(
    busy: Boolean,
    busyLabel: String?,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AiepNavy,
            contentColor = AiepSurface,
            disabledContainerColor = AiepNavy,
            disabledContentColor = AiepSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AiepSurface,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = busyLabel ?: stringResource(R.string.export_status_preparing),
                    color = AiepSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download_24),
                    contentDescription = null,
                    tint = AiepSurface,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.action_download_pdf),
                    color = AiepSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LiveCaptionBlock(snapshot: CaptionPacerSnapshot) {
    val annotated = remember(snapshot.committedTail, snapshot.currentTail) {
        buildAnnotatedString {
            val committed = snapshot.committedTail
            val current = snapshot.currentTail
            if (committed.isNotEmpty()) {
                withStyle(SpanStyle(color = AiepSurface)) { append(committed) }
            }
            if (current.isNotEmpty()) {
                if (committed.isNotEmpty()) append(" ")
                withStyle(SpanStyle(color = AiepSurface.copy(alpha = 0.65f))) { append(current) }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AiepNavyDeep)
            .border(1.dp, AiepNavy.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true),
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
    localOnly: Boolean,
    onManualClick: () -> Unit,
    onScanRequest: () -> Unit,
    onLocalOnlyChange: (Boolean) -> Unit,
) {
    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel(text = "Conexión")

            ScanQrButton(enabled = !streaming && !localOnly, onClick = onScanRequest)

            ManualCodeSummaryRow(
                code = sessionId.text.trim().uppercase(),
                streaming = streaming,
                localOnly = localOnly,
                onClick = onManualClick,
            )

            LocalOnlySwitchRow(
                checked = localOnly,
                enabled = !streaming,
                onCheckedChange = onLocalOnlyChange,
            )
        }
    }
}

@Composable
private fun ManualCodeSummaryRow(
    code: String,
    streaming: Boolean,
    localOnly: Boolean,
    onClick: () -> Unit,
) {
    val status = when {
        localOnly -> "Desactivado en modo celular"
        streaming -> "Código bloqueado durante la grabación"
        code.length == 6 -> "Código listo"
        else -> "No configurado"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiepCreamSoft, RoundedCornerShape(10.dp))
            .clickable(enabled = !streaming && !localOnly, onClick = onClick)
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
            color = if (localOnly) AiepMuted else AiepNavy,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LocalOnlySwitchRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = AiepCreamSoft,
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = AiepLine,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Solo en este celular",
                color = AiepInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Transcribe y guarda en esta app, sin conectar al PC.",
                color = AiepMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AiepSurface,
                checkedTrackColor = AiepRed,
                checkedBorderColor = AiepRed,
                uncheckedThumbColor = AiepSurface,
                uncheckedTrackColor = AiepMuted.copy(alpha = 0.36f),
                uncheckedBorderColor = AiepLine,
            ),
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
    var showFullSheet by remember { mutableStateOf(false) }
    var fullText by remember(meta.captionCount, showFullSheet) { mutableStateOf<String?>(null) }
    val liveCaption by CaptionPreviewBus.preview.collectAsStateWithLifecycle()
    val showLiveCaption = streaming && meta.mode == CaptureMode.Speech
    val captionPacer = remember(sessionId) { CaptionPacer(maxVisibleWords = 40) }
    var pacedCaption by remember(sessionId) { mutableStateOf(CaptionPacerSnapshot()) }

    LaunchedEffect(meta.captionCount, sessionId, streaming) {
        if (!streaming) {
            lines = repo.readRecentLines(sessionId, limit = 5)
        }
    }

    LaunchedEffect(showFullSheet, meta.captionCount, sessionId, streaming) {
        if (showFullSheet && !streaming) fullText = repo.readMarkdown(sessionId)
    }

    LaunchedEffect(showLiveCaption) {
        pacedCaption = if (showLiveCaption) {
            captionPacer.set("Escuchando...", instant = true)
        } else {
            captionPacer.reset()
        }
    }

    LaunchedEffect(showLiveCaption, liveCaption.text, liveCaption.isFinal) {
        if (showLiveCaption && liveCaption.text.isNotBlank()) {
            pacedCaption = captionPacer.set(liveCaption.text, isFinal = liveCaption.isFinal)
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
                val snapshotForUi = if (pacedCaption.visibleText.isBlank()) {
                    CaptionPacerSnapshot(
                        visibleText = "Escuchando...",
                        committedTail = "Escuchando...",
                    )
                } else {
                    pacedCaption
                }
                LiveCaptionBlock(snapshot = snapshotForUi)
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
                    .clickable { showFullSheet = true }
                    .padding(top = 4.dp),
            )
        }
    }

    if (showFullSheet) {
        FullTranscriptSheet(
            title = meta.name,
            markdown = fullText,
            onDismiss = { showFullSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullTranscriptSheet(
    title: String,
    markdown: String?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AiepSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Transcripción",
                    color = AiepMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = title,
                    color = AiepNavy,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 520.dp)
                    .background(AiepCreamSoft, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                if (markdown == null) {
                    Text(
                        text = "Cargando…",
                        color = AiepMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = markdown,
                        color = AiepInk,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
