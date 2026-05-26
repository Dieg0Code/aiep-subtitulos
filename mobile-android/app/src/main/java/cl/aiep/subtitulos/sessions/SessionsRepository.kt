package cl.aiep.subtitulos.sessions

import android.content.Context
import android.util.Log
import cl.aiep.subtitulos.CaptureMode
import cl.aiep.subtitulos.LOG_TAG
import cl.aiep.subtitulos.audio.CaptionProcessor
import cl.aiep.subtitulos.audio.MergeResult
import cl.aiep.subtitulos.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsRepository private constructor(private val app: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val snippetChars = 120
    private val transcriptCap = 500
    private val transcriptMergeGapMs = 4_000L

    private val sessionsDir: File by lazy {
        File(app.filesDir, "sessions").apply { mkdirs() }
    }

    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions.asStateFlow()

    init {
        ioScope.launch { reloadIndex() }
    }

    private suspend fun reloadIndex() {
        val raw = AppPreferences.sessionsIndexFlow(app).first()
        val parsed = SessionMeta.listFromJson(raw)
        _sessions.value = parsed.sortedByDescending { it.updatedAt }
    }

    private suspend fun persistIndex(items: List<SessionMeta>) {
        AppPreferences.setSessionsIndex(app, SessionMeta.listToJson(items))
        _sessions.value = items.sortedByDescending { it.updatedAt }
    }

    suspend fun createSession(mode: CaptureMode, relaySessionId: String?): SessionMeta = mutex.withLock {
        val now = System.currentTimeMillis()
        val meta = SessionMeta(
            id = generateId(),
            name = defaultName(now),
            createdAt = now,
            updatedAt = now,
            captionCount = 0,
            lastSnippet = "",
            durationMs = 0L,
            mode = mode,
            relaySessionId = relaySessionId,
        )
        writeMarkdownHeader(meta)
        val updated = _sessions.value + meta
        persistIndex(updated)
        meta
    }

    suspend fun updateSessionMode(id: String, mode: CaptureMode, relaySessionId: String? = null) = mutex.withLock {
        val current = _sessions.value.firstOrNull { it.id == id } ?: return@withLock
        if (current.mode == mode && (relaySessionId == null || current.relaySessionId == relaySessionId)) return@withLock
        val refreshed = current.copy(
            mode = mode,
            relaySessionId = relaySessionId ?: current.relaySessionId,
            updatedAt = System.currentTimeMillis(),
        )
        val updated = _sessions.value.map { if (it.id == id) refreshed else it }
        persistIndex(updated)
        rewriteMarkdownWithFreshHeader(refreshed)
    }

    suspend fun renameSession(id: String, name: String) = mutex.withLock {
        val trimmed = name.trim().ifEmpty { return@withLock }
        val current = _sessions.value
        val target = current.firstOrNull { it.id == id } ?: return@withLock
        val refreshed = target.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        val updated = current.map { if (it.id == id) refreshed else it }
        persistIndex(updated)
        rewriteMarkdownWithFreshHeader(refreshed)
    }

    suspend fun deleteSession(id: String) = mutex.withLock {
        if (ActiveSessionTracker.isActive(id)) return@withLock
        val updated = _sessions.value.filterNot { it.id == id }
        persistIndex(updated)
        runCatching { markdownFile(id).delete() }
    }

    suspend fun appendFinalCaption(id: String, text: String, timestampMs: Long) = mutex.withLock {
        val trimmed = CaptionProcessor.normalize(text)
        if (trimmed.isEmpty()) return@withLock
        val current = _sessions.value.firstOrNull { it.id == id } ?: return@withLock
        if (current.mode != CaptureMode.Speech) return@withLock

        val existing = withContext(Dispatchers.IO) {
            runCatching { markdownFile(id).readText() }.getOrDefault("")
        }
        val bodyLines = transcriptLines(existing)
        val lastLine = bodyLines.lastOrNull()
        val lastText = lastLine?.let(::stripTimestamp).orEmpty()
        val withinGap = current.captionCount > 0 && timestampMs - current.updatedAt < transcriptMergeGapMs

        val merge = if (lastLine == null) {
            MergeResult.NewLine(trimmed)
        } else {
            CaptionProcessor.mergeLine(lastText, trimmed, withinGap)
        }

        val time = TIME_FORMAT.format(Date(timestampMs))
        val newDuration = (System.currentTimeMillis() - current.createdAt)
            .coerceAtLeast(current.durationMs)
        val newCount: Int
        val newSnippet: String
        val nextBody: List<String>?

        when (merge) {
            MergeResult.Skip -> return@withLock
            is MergeResult.Merged -> {
                newCount = current.captionCount
                newSnippet = snippetOf(merge.text)
                nextBody = bodyLines.dropLast(1) + "[$time] ${merge.text}"
            }
            is MergeResult.NewLine -> {
                withContext(Dispatchers.IO) {
                    markdownFile(id).appendText("[$time] ${merge.text}\n")
                }
                newCount = current.captionCount + 1
                newSnippet = snippetOf(merge.text)
                nextBody = null
            }
        }

        val refreshed = current.copy(
            captionCount = newCount,
            lastSnippet = newSnippet,
            durationMs = newDuration,
            updatedAt = timestampMs,
        )
        val updated = _sessions.value.map { if (it.id == id) refreshed else it }
        persistIndex(updated)
        if (nextBody != null) {
            rewriteMarkdownWithFreshHeader(refreshed, nextBody)
        }

        if (newCount > transcriptCap) {
            Log.w(LOG_TAG, "Session $id exceeded $transcriptCap captions; consider archiving")
        }
    }

    suspend fun markSessionStopped(id: String) = mutex.withLock {
        val current = _sessions.value.firstOrNull { it.id == id } ?: return@withLock
        val refreshed = current.copy(
            durationMs = (System.currentTimeMillis() - current.createdAt).coerceAtLeast(current.durationMs),
            updatedAt = System.currentTimeMillis(),
        )
        val updated = _sessions.value.map { if (it.id == id) refreshed else it }
        persistIndex(updated)
        rewriteMarkdownWithFreshHeader(refreshed)
    }

    suspend fun readMarkdown(id: String): String = withContext(Dispatchers.IO) {
        runCatching { markdownFile(id).readText() }.getOrDefault("")
    }

    suspend fun readRecentLines(id: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        runCatching { transcriptLines(markdownFile(id).readText()).takeLast(limit) }
            .getOrDefault(emptyList())
    }

    private fun markdownFile(id: String): File = File(sessionsDir, "$id.md")

    private fun writeMarkdownHeader(meta: SessionMeta) {
        rewriteMarkdownWithFreshHeader(meta, emptyList())
    }

    private fun rewriteMarkdownWithFreshHeader(meta: SessionMeta) {
        val existing = runCatching { markdownFile(meta.id).readText() }.getOrDefault("")
        rewriteMarkdownWithFreshHeader(meta, transcriptLines(existing))
    }

    private fun rewriteMarkdownWithFreshHeader(meta: SessionMeta, bodyLines: List<String>) {
        val sb = StringBuilder()
        sb.append("# AIEP Subtítulos · ").append(meta.name).append('\n').append('\n')
        sb.append("- **ID**: ").append(meta.id).append('\n')
        sb.append("- **Modo**: ").append(modeLabel(meta.mode)).append('\n')
        sb.append("- **Inicio**: ").append(DATETIME_FORMAT.format(Date(meta.createdAt))).append('\n')
        sb.append("- **Duración**: ").append(formatDuration(meta.durationMs)).append('\n')
        sb.append("- **Líneas**: ").append(meta.captionCount).append('\n')
        meta.relaySessionId?.let { sb.append("- **Código QR**: ").append(it).append('\n') }
        sb.append('\n')
        sb.append("## Transcripción").append('\n').append('\n')
        if (meta.mode != CaptureMode.Speech) {
            sb.append(PCM_MESSAGE).append('\n')
        } else {
            bodyLines.forEach { sb.append(it).append('\n') }
        }
        markdownFile(meta.id).writeText(sb.toString())
    }

    private fun transcriptLines(markdown: String): List<String> {
        val lines = markdown.lineSequence().toList()
        val start = lines.indexOfFirst { it.trim().startsWith("## Transcrip") }
        val body = if (start >= 0) {
            lines.drop(start + 1)
        } else {
            lines.filter { it.trimStart().matches(TRANSCRIPT_LINE_PATTERN) }
        }
        return body
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filterNot { it == PCM_MESSAGE }
    }

    private fun stripTimestamp(line: String): String =
        line.replace(Regex("^\\[[^\\]]+]\\s*"), "").trim()

    private fun snippetOf(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length <= snippetChars) cleaned
        else cleaned.substring(0, snippetChars - 1) + "…"
    }

    private fun modeLabel(mode: CaptureMode): String = when (mode) {
        CaptureMode.Speech -> "Speech (es-CL)"
        CaptureMode.Pcm -> "Whisper PCM"
    }

    private fun formatDuration(ms: Long): String {
        if (ms < 1_000) return "0 s"
        val totalSeconds = ms / 1_000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours} h ${minutes} min"
            minutes > 0 -> "${minutes} min ${seconds} s"
            else -> "${seconds} s"
        }
    }

    private fun generateId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rnd = (0..0xFFFFFF).random().toString(16).padStart(6, '0')
        return "ses-$ts-$rnd"
    }

    private fun defaultName(now: Long): String =
        "Sesión " + NAME_FORMAT.format(Date(now))

    companion object {
        private val NAME_FORMAT = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("es", "CL"))
        private val DATETIME_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "CL"))
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale("es", "CL"))
        private val TRANSCRIPT_LINE_PATTERN = Regex("^\\[\\d{2}:\\d{2}:\\d{2}]\\s+.+")
        private const val PCM_MESSAGE =
            "_Esta sesión usó modo Whisper. La transcripción completa está en el PC._"

        @Volatile private var INSTANCE: SessionsRepository? = null

        fun get(context: Context): SessionsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val activeSessionId: Flow<String?> get() = ActiveSessionTracker.activeId
}
