package cl.aiep.subtitulos.audio

data class CaptionPacerSnapshot(
    val visibleText: String = "",
    val pendingWords: Int = 0,
    val committedTail: String = "",
    val currentTail: String = "",
)

class CaptionPacer(
    private val maxVisibleWords: Int = 40,
    private val maxHistoryWords: Int = 420,
) {
    private val committed = mutableListOf<String>()
    private val current = mutableListOf<String>()
    private var placeholder: List<String>? = null

    val snapshot: CaptionPacerSnapshot
        get() {
            placeholder?.let { ph ->
                val text = ph.joinToString(" ")
                return CaptionPacerSnapshot(
                    visibleText = text,
                    pendingWords = 0,
                    committedTail = text,
                    currentTail = "",
                )
            }
            val totalSize = committed.size + current.size
            val keep = if (totalSize <= maxVisibleWords) totalSize else maxVisibleWords
            val currentVisible = minOf(current.size, keep)
            val committedVisible = keep - currentVisible
            val committedTail = committed.takeLast(committedVisible)
            val currentTail = current.takeLast(currentVisible)
            val visible = committedTail + currentTail
            return CaptionPacerSnapshot(
                visibleText = visible.joinToString(" "),
                pendingWords = 0,
                committedTail = committedTail.joinToString(" "),
                currentTail = currentTail.joinToString(" "),
            )
        }

    fun set(text: String, isFinal: Boolean = false, instant: Boolean = false): CaptionPacerSnapshot {
        if (instant) {
            committed.clear()
            current.clear()
            placeholder = toWords(text)
            return snapshot
        }

        placeholder = null
        val incoming = toWords(text).filterNot(::isEmptyWord)
        current.clear()
        current.addAll(incoming)

        if (isFinal) {
            committed.addAll(current)
            current.clear()
            trimCommitted()
        }
        return snapshot
    }

    fun tick(): CaptionPacerSnapshot = snapshot

    fun reset(): CaptionPacerSnapshot {
        committed.clear()
        current.clear()
        placeholder = null
        return snapshot
    }

    private fun toWords(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun isEmptyWord(word: String): Boolean =
        CaptionProcessor.wordKey(word).isEmpty()

    private fun trimCommitted() {
        if (committed.size <= maxHistoryWords) return
        val keep = committed.takeLast(maxHistoryWords)
        committed.clear()
        committed.addAll(keep)
    }
}
